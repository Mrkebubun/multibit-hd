package org.multibit.hd.core.services;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.eventbus.Subscribe;
import com.google.protobuf.InvalidProtocolBufferException;
import com.googlecode.jcsv.writer.CSVEntryConverter;
import org.bitcoin.protocols.payments.Protos;
import org.bitcoinj.core.*;
import org.bitcoinj.protocols.payments.PaymentProtocolException;
import org.bitcoinj.protocols.payments.PaymentSession;
import org.joda.time.DateTime;
import org.multibit.hd.core.concurrent.SafeExecutors;
import org.multibit.hd.core.crypto.EncryptedFileReaderWriter;
import org.multibit.hd.core.dto.*;
import org.multibit.hd.core.events.*;
import org.multibit.hd.core.exceptions.EncryptedFileReaderWriterException;
import org.multibit.hd.core.exceptions.ExceptionHandler;
import org.multibit.hd.core.exceptions.PaymentsLoadException;
import org.multibit.hd.core.exceptions.PaymentsSaveException;
import org.multibit.hd.core.exchanges.ExchangeKey;
import org.multibit.hd.core.files.SecureFiles;
import org.multibit.hd.core.managers.ExportManager;
import org.multibit.hd.core.managers.InstallationManager;
import org.multibit.hd.core.managers.WalletManager;
import org.multibit.hd.core.store.Payments;
import org.multibit.hd.core.store.PaymentsProtobufSerializer;
import org.multibit.hd.core.store.TransactionInfo;
import org.multibit.hd.core.utils.Coins;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import java.io.*;
import java.math.BigDecimal;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * <p>Service to provide the following to GUI classes:</p>
 * <ul>
 * <li>list Transactions in the current wallet</li>
 * </ul>
 * <p/>
 * Most of the functionality is provided by WalletManager and BackupManager.
 */
public class WalletService extends AbstractService {

  private static final Logger log = LoggerFactory.getLogger(WalletService.class);

  /**
   * The name of the directory (within the wallet directory) that contains the payments database
   */
  public final static String PAYMENTS_DIRECTORY_NAME = "payments";

  /**
   * The name of the protobuf file containing additional payments information, AES encrypted
   */
  public static final String PAYMENTS_DATABASE_NAME = "payments.aes";

  /**
   * The text separator used in localising To: and By: prefices
   */
  public static final String PREFIX_SEPARATOR = ": ";

  /**
   * The subdirectory in the PAYMENTS_DIRECTORY_NAME directory that contains the raw BIP70 payment requests
   */
  public static final String BIP70_PAYMENT_REQUEST_DIRECTORY = "bip70";

  /**
   * The suffix for the serialised BIP70 payment request
   */
  public static final String BIP70_PAYMENT_REQUEST_SUFFIX = ".aes";

  /**
   * The Bitcoin network parameters
   */
  private final NetworkParameters networkParameters;

  /**
   * The location of the backing write for the payments
   */
  private File backingStoreFile;

  /**
   * The serializer for the backing store
   */
  private PaymentsProtobufSerializer protobufSerializer;

  /**
   * The MBHD payment requests in a map, indexed by the bitcoin address
   */
  private final Map<Address, MBHDPaymentRequestData> MBHDPaymentRequestMap = Collections.synchronizedMap(new HashMap<Address, MBHDPaymentRequestData>());

  /**
   * The additional transaction information, in the form of a map, index by the transaction hash
   */
  private final ConcurrentHashMap<String, TransactionInfo> transactionInfoMap = new ConcurrentHashMap<>();

  /**
   * The payment protocol (BIP70)payment requests
   */
  private final Map<UUID, PaymentRequestData> paymentRequestDataMap = Collections.synchronizedMap(new HashMap<UUID, PaymentRequestData>());

  /**
   * The wallet id that this WalletService is using
   */
  private WalletId walletId;

  /**
   * The undo stack for undeleting payment requests
   */
  private final Stack<MBHDPaymentRequestData> undoDeletePaymentRequestStack = new Stack<>();

  /**
   * The last seen payments data
   */
  private Set<PaymentData> lastSeenPaymentDataSet = Sets.newHashSet();

  /**
   * Handles wallet operations
   */
  private static final ExecutorService executorService = SafeExecutors.newSingleThreadExecutor("wallet-service");

  public WalletService(NetworkParameters networkParameters) {

    super();

    Preconditions.checkNotNull(networkParameters, "'networkParameters' must be present");

    this.networkParameters = networkParameters;

  }

  @Override
  protected boolean startInternal() {

    Preconditions.checkNotNull(walletId, "No walletId - have you called initialise() first?");

    return true;
  }

  @Override
  protected boolean shutdownNowInternal(ShutdownEvent.ShutdownType shutdownType) {

    if (WalletManager.INSTANCE.getCurrentWalletSummary().isPresent()) {
      try {
        writePayments();
      } catch (PaymentsSaveException pse) {
        // Cannot do much as shutting down
        log.error("Failed to write payments.", pse);
      }
    }

    // Always treat as a hard shutdown
    return true;
  }

  /**
   * Initialise the wallet service with a user data directory and a wallet id so that it knows where to put files etc
   *
   * @param walletId the walletId to use for this WalletService
   */
  public void initialise(File applicationDataDirectory, WalletId walletId) {
    Preconditions.checkNotNull(applicationDataDirectory, "'applicationDataDirectory' must be present");
    Preconditions.checkNotNull(walletId, "'walletId' must be present");

    this.walletId = walletId;

    // Work out where to write the contacts for this wallet id.
    String walletRoot = WalletManager.createWalletRoot(walletId);

    File walletDirectory = WalletManager.getOrCreateWalletDirectory(applicationDataDirectory, walletRoot);

    File paymentsDirectory = new File(walletDirectory.getAbsolutePath() + File.separator + PAYMENTS_DIRECTORY_NAME);
    SecureFiles.verifyOrCreateDirectory(paymentsDirectory);

    this.backingStoreFile = new File(paymentsDirectory.getAbsolutePath() + File.separator + PAYMENTS_DATABASE_NAME);

    protobufSerializer = new PaymentsProtobufSerializer();

    if (backingStoreFile.exists()) {
      readPayments();
    }
  }

  /**
   * <p>Get all the payments (payments and payment requests) in the current wallet.</p>
   * <h3>NOTE: This is moderately expensive so don't call it indiscriminately</h3>
   */
  public Set<PaymentData> getPaymentDataSet() {

    // See if there is a current wallet
    WalletManager walletManager = WalletManager.INSTANCE;

    Optional<WalletSummary> currentWalletSummary = walletManager.getCurrentWalletSummary();
    if (!currentWalletSummary.isPresent()) {
      // No wallet is present
      return Sets.newHashSet();
    }

    // Wallet is present
    WalletSummary walletSummary = currentWalletSummary.get();
    Wallet wallet = walletSummary.getWallet();

    // There should be a wallet
    Preconditions.checkNotNull(wallet, "There is no wallet to process");

    // Get all the transactions in the wallet
    Set<Transaction> transactions = wallet.getTransactions(true);

    // Adapted transaction data to return
    Set<TransactionData> transactionDataSet = Sets.newHashSet();

    if (transactions != null) {
      for (Transaction transaction : transactions) {
        // Adapt the transaction - adding on matching MBHDPaymentRequests and BIP70 PaymentRequests
        TransactionData transactionData = adaptTransaction(wallet, transaction);
        transactionDataSet.add(transactionData);
      }
    }

    // Determine which MBHDPaymentRequests have not been fully funded (these will appear as independent entities in the UI)
    Set<MBHDPaymentRequestData> paymentRequestsNotFullyFunded = Sets.newHashSet();
    for (MBHDPaymentRequestData baseMBHDPaymentRequestData : MBHDPaymentRequestMap.values()) {
      if (baseMBHDPaymentRequestData.getPaidAmountCoin().compareTo(baseMBHDPaymentRequestData.getAmountCoin()) < 0) {
        paymentRequestsNotFullyFunded.add(baseMBHDPaymentRequestData);
      }
    }
    // Union the transactionData set and paymentData set
    lastSeenPaymentDataSet = Sets.union(transactionDataSet, paymentRequestsNotFullyFunded);

    Set<PaymentData> bip70PaymentData = Sets.newHashSet();
    for (PaymentData paymentData : paymentRequestDataMap.values()) {
      // If there is a tx hash then the bip70 payment is not a 'top level' object - not shown in payments table
      if (!((PaymentRequestData)paymentData).getTransactionHashOptional().isPresent()) {
        bip70PaymentData.add(paymentData);
      }
    }
    log.debug("Adding in {} BIP70 payment data rows", bip70PaymentData.size());
    lastSeenPaymentDataSet = Sets.union(lastSeenPaymentDataSet, bip70PaymentData);

    //log.debug("lastSeenPaymentDataSet:\n" + lastSeenPaymentDataSet.toString());
    return lastSeenPaymentDataSet;
  }

  public int getPaymentDataSetSize() {
    if (lastSeenPaymentDataSet == null) {
      getPaymentDataSet();
    }
    return lastSeenPaymentDataSet.size();
  }

  /**
   * Subset the supplied payments and sort by date, descending
   * (Sorting by amount coin is also done to make the order unique, within same date. This is to stop the order 'flicking' on sync)
   *
   * @param subsettingPaymentType if PaymentType.SENDING return all sending payments for the last 24 hours
   *                    if PaymentType.RECEIVING return all requesting and receiving payments for the last 24 hours
   */
  public List<PaymentData> subsetPaymentsAndSort(Set<PaymentData> paymentDataSet, PaymentType subsettingPaymentType) {

    // Subset to the required type of payment
    List<PaymentData> subsetPaymentDataList = Lists.newArrayList();

    if (subsettingPaymentType != null) {
      DateTime aDayAgo = DateTime.now().minusHours(24);

      for (PaymentData paymentData : paymentDataSet) {

        if (subsettingPaymentType == PaymentType.SENDING
                && (paymentData.getType() == PaymentType.THEY_REQUESTED || paymentData.getType() == PaymentType.SENDING)
                 && paymentData.getDate().isAfter(aDayAgo) ) {

          subsetPaymentDataList.add(paymentData);

        } else if (subsettingPaymentType == PaymentType.RECEIVING) {

          if (paymentData.getType() == PaymentType.YOU_REQUESTED
                  || paymentData.getType() == PaymentType.RECEIVING
                  || paymentData.getType() == PaymentType.PART_PAID) {

            if (paymentData.getDate().isAfter(aDayAgo)) {
              subsetPaymentDataList.add(paymentData);
            }
          }
        }
      }
    }

    Collections.sort(subsetPaymentDataList, new PaymentComparator());

    return subsetPaymentDataList;
  }

  /**
   * @param query The text fragment to match (case-insensitive, anywhere in the name)
   * @return A filtered set of Payments for the given query
   */
  public List<PaymentData> filterPaymentsByContent(String query) {

    String lowerQuery = query.toLowerCase();

    List<PaymentData> filteredPayments = Lists.newArrayList();

    for (PaymentData paymentData : lastSeenPaymentDataSet) {

      boolean isDescriptionMatched = paymentData.getDescription().toLowerCase().contains(lowerQuery);
      boolean isNoteMatched = paymentData.getNote().toLowerCase().contains(lowerQuery);

      boolean isQrCodeLabelMatched = false;
      boolean isPaymentAddressMatched = false;
      boolean isOutputAddressMatched = false;
      boolean isRawTransactionMatched = false;

      if (paymentData instanceof MBHDPaymentRequestData) {

        MBHDPaymentRequestData MBHDPaymentRequestData = (MBHDPaymentRequestData) paymentData;
        isQrCodeLabelMatched = MBHDPaymentRequestData.getLabel().toLowerCase().contains(lowerQuery);

        // Exact match only
        isPaymentAddressMatched = MBHDPaymentRequestData.getAddress().toString().equals(query);

      } else if (paymentData instanceof TransactionData) {

        TransactionData transactionData = (TransactionData) paymentData;
        isOutputAddressMatched = Joiner.on(" ").join(transactionData.getOutputAddresses()).toLowerCase().contains(lowerQuery);
        isRawTransactionMatched = transactionData.getRawTransaction().toLowerCase().contains(lowerQuery);

      }
      if (isDescriptionMatched
              || isNoteMatched
              || isQrCodeLabelMatched
              || isPaymentAddressMatched
              || isOutputAddressMatched
              || isRawTransactionMatched
              ) {
        filteredPayments.add(paymentData);
      }
    }

    Collections.sort(filteredPayments, new PaymentComparator());

    return filteredPayments;
  }

  /**
   * Adapt a bitcoinj transaction to a TransactionData DTO.
   * Also merges in any transactionInfo available.
   * Also checks if this transaction funds any payment requests
   *
   * @param wallet      the current wallet
   * @param transaction the transaction to adapt
   * @return TransactionData the transaction data
   */
  public TransactionData adaptTransaction(Wallet wallet, Transaction transaction) {

    // Tx id
    String transactionHashAsString = transaction.getHashAsString();

    // UpdateTime
    Date updateTime = transaction.getUpdateTime();

    // Amount BTC
    Coin amountBTC = transaction.getValue(wallet);

    // Fiat amount
    FiatPayment amountFiat = calculateFiatPaymentAndAddTransactionInfo(amountBTC, transactionHashAsString);

    TransactionConfidence transactionConfidence = transaction.getConfidence();

    // Depth
    int depth = 0; // By default not in a block
    TransactionConfidence.ConfidenceType confidenceType = TransactionConfidence.ConfidenceType.UNKNOWN;

    if (transactionConfidence != null) {
      confidenceType = transaction.getConfidence().getConfidenceType();
      if (TransactionConfidence.ConfidenceType.BUILDING.equals(confidenceType)) {
        depth = transaction.getConfidence().getDepthInBlocks();
      }
    }

    // Payment status
    PaymentStatus paymentStatus = calculateStatus(transaction.getConfidence().getConfidenceType(), depth, transaction.getConfidence().numBroadcastPeers());

    // Payment type
    PaymentType paymentType = calculatePaymentType(amountBTC, depth);

    // Mining fee
    Optional<Coin> miningFee = calculateMiningFee(paymentType, transactionHashAsString);

    // Client fee
    Optional<Coin> clientFee = calculateClientFee(paymentType, transactionHashAsString);

    // Description +
    // Ensure that any payment requests that are funded by this transaction know about it
    // (The payment request knows about the transactions that fund it but not the reverse)

    String description = calculateDescriptionAndUpdatePaymentRequests(wallet, transaction, transactionHashAsString, paymentType, amountBTC);
    // Also works out outputAddresses

    // Include the raw serialized form of the transaction for lowest level viewing
    String rawTransaction = transaction.toString() + "\n" + Utils.HEX.encode(transaction.bitcoinSerialize()) + "\n";

    int size = -1;
    ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    try {
      transaction.bitcoinSerialize(byteOutputStream);
      size = byteOutputStream.size();
    } catch (IOException e) {
      log.error("Failed to serialize transaction", e);
    }

    List<Address> outputAddresses = calculateOutputAddresses(transaction);

    // Create the DTO from the raw transaction info
    TransactionData transactionData = new TransactionData(
            transactionHashAsString,
            new DateTime(updateTime),
            paymentStatus,
            amountBTC,
            amountFiat,
            miningFee,
            clientFee,
            confidenceType,
            paymentType,
            description,
            transaction.isCoinBase(),
            outputAddresses,
            rawTransaction,
            size,
            false
    );

    // Note - from the transactionInfo (if present)
    String note = calculateNote(transactionData, transactionHashAsString);
    transactionData.setNote(note);

    return transactionData;
  }

  /**
   * <p>Calculate the PaymentStatus of the transaction:</p>
   * <ul>
   * <li>RED: tx is dead, double spend, failed to be transmitted to the network etc</li>
   * <li>AMBER: tx is unconfirmed</li>
   * <li>GREEN: tx has one or more confirmations</li>
   * </ul>
   *
   * @param confidenceType the Bitcoinj ConfidenceType  to use to work out the status
   * @param depth          depth in blocks of the transaction (1 is most recent)
   * @return status of the transaction
   */
  public static PaymentStatus calculateStatus(TransactionConfidence.ConfidenceType confidenceType, int depth, int numberOfPeers) {

    if (confidenceType != null) {

      if (TransactionConfidence.ConfidenceType.BUILDING.equals(confidenceType)) {

        // Confirmed
        final PaymentStatus paymentStatus;
        if (depth == 1) {
          paymentStatus = new PaymentStatus(RAGStatus.GREEN, CoreMessageKey.CONFIRMED_BY_ONE_BLOCK);
        } else {
          paymentStatus = new PaymentStatus(RAGStatus.GREEN, CoreMessageKey.CONFIRMED_BY_SEVERAL_BLOCKS);
          paymentStatus.setStatusData(new Object[]{depth});
        }
        paymentStatus.setDepth(depth);
        return paymentStatus;

      } else if (TransactionConfidence.ConfidenceType.PENDING.equals(confidenceType)) {
        if (numberOfPeers >= 2) {
          // Seen by the network but not confirmed yet
          PaymentStatus paymentStatus = new PaymentStatus(RAGStatus.AMBER, CoreMessageKey.BROADCAST);
          paymentStatus.setStatusData(new Object[]{numberOfPeers});
          return paymentStatus;
        } else {
          // Not definitely out in the network (seen by one peer is probably the peer first broadcast to, which will INV it back
          return new PaymentStatus(RAGStatus.AMBER, CoreMessageKey.NOT_BROADCAST);
        }
      } else if (TransactionConfidence.ConfidenceType.DEAD.equals(confidenceType)) {
        // Dead
        return new PaymentStatus(RAGStatus.RED, CoreMessageKey.DEAD);
      } else if (TransactionConfidence.ConfidenceType.UNKNOWN.equals(confidenceType)) {
        // Unknown
        return new PaymentStatus(RAGStatus.AMBER, CoreMessageKey.UNKNOWN);
      }
    } else {
      // No transaction status - don't know
      return new PaymentStatus(RAGStatus.AMBER, CoreMessageKey.UNKNOWN);
    }

    // Unknown
    return new PaymentStatus(RAGStatus.AMBER, CoreMessageKey.UNKNOWN);

  }

  private PaymentType calculatePaymentType(Coin amountBTC, int depth) {
    PaymentType paymentType;
    if (amountBTC.compareTo(Coin.ZERO) < 0) {
      // Debit
      if (depth == 0) {
        paymentType = PaymentType.SENDING;
      } else {
        paymentType = PaymentType.SENT;
      }
    } else {
      // Credit
      if (depth == 0) {
        paymentType = PaymentType.RECEIVING;
      } else {
        paymentType = PaymentType.RECEIVED;
      }
    }
    return paymentType;
  }

  private String calculateDescriptionAndUpdatePaymentRequests(
          Wallet wallet,
          Transaction transaction,
          String transactionHashAsString,
          PaymentType paymentType,
          Coin amountBTC
  ) {

    StringBuilder description = new StringBuilder();
    if (paymentType == PaymentType.RECEIVING || paymentType == PaymentType.RECEIVED) {
      String addresses = "";

      boolean descriptiveTextIsAvailable = false;
      if (transaction.getOutputs() != null) {
        for (TransactionOutput transactionOutput : transaction.getOutputs()) {
          if (transactionOutput.isMine(wallet)) {
            Address receivingAddress = transactionOutput.getScriptPubKey().getToAddress(networkParameters);
            addresses = addresses + " " + receivingAddress;

            // Check if this output funds any payment requests;
            MBHDPaymentRequestData MBHDPaymentRequestData = MBHDPaymentRequestMap.get(receivingAddress);
            if (MBHDPaymentRequestData != null) {
              // Yes - this output funds a payment address
              if (!MBHDPaymentRequestData.getPayingTransactionHashes().contains(transactionHashAsString)) {
                // We have not yet added this tx to the total paid amount
                MBHDPaymentRequestData.getPayingTransactionHashes().add(transactionHashAsString);
                MBHDPaymentRequestData.setPaidAmountCoin(MBHDPaymentRequestData.getPaidAmountCoin().add(amountBTC));
              }

              if (MBHDPaymentRequestData.getLabel() != null && MBHDPaymentRequestData.getLabel().length() > 0) {
                descriptiveTextIsAvailable = true;
                description
                        .append(MBHDPaymentRequestData.getLabel())
                        .append(" ");
              }
              if (MBHDPaymentRequestData.getNote() != null && MBHDPaymentRequestData.getNote().length() > 0) {
                descriptiveTextIsAvailable = true;
                description
                        .append(MBHDPaymentRequestData.getNote())
                        .append(" ");
              }
            }
          }
        }
      }

      if (!descriptiveTextIsAvailable) {
        // TODO localise
        description
                .append("By")
                .append(PREFIX_SEPARATOR)
                .append(addresses.trim());
      }
    } else {
      // Sent
      // TODO localise
      description
              .append("To")
              .append(PREFIX_SEPARATOR);
      if (transaction.getOutputs() != null) {
        for (TransactionOutput transactionOutput : transaction.getOutputs()) {
          description
                  .append(" ")
                  .append(transactionOutput.getScriptPubKey().getToAddress(networkParameters));
        }
      }
    }
    return description.toString();
  }

  private List<Address> calculateOutputAddresses(Transaction transaction) {
    List<Address> outputAddresses = Lists.newArrayList();

    if (transaction.getOutputs() != null) {
      for (TransactionOutput transactionOutput : transaction.getOutputs()) {
        outputAddresses.add(transactionOutput.getScriptPubKey().getToAddress(networkParameters));
      }
    }

    return outputAddresses;
  }

  private FiatPayment calculateFiatPaymentEquivalent(Coin amountBTC) {
    FiatPayment amountFiat = new FiatPayment();

     // Work it out from the current settings
     amountFiat.setExchangeName(Optional.of(ExchangeKey.current().getExchangeName()));

     if (CoreServices.getApplicationEventService() != null) {
       Optional<ExchangeRateChangedEvent> exchangeRateChangedEvent = CoreServices.getApplicationEventService().getLatestExchangeRateChangedEvent();
       if (exchangeRateChangedEvent.isPresent() && exchangeRateChangedEvent.get().getRate() != null) {
         amountFiat.setRate(Optional.of(exchangeRateChangedEvent.get().getRate().toString()));

         if (amountBTC != null) {
           BigDecimal localAmount = Coins.toLocalAmount(amountBTC, exchangeRateChangedEvent.get().getRate());
           if (localAmount.compareTo(BigDecimal.ZERO) != 0) {
             amountFiat.setAmount(Optional.of(localAmount));
           } else {
             amountFiat.setAmount(Optional.<BigDecimal>absent());
           }
         } else {
           amountFiat.setAmount(Optional.<BigDecimal>absent());
         }
         amountFiat.setCurrency(Optional.of(exchangeRateChangedEvent.get().getCurrency()));
       } else {
         amountFiat.setRate(Optional.<String>absent());
         amountFiat.setAmount(Optional.<BigDecimal>absent());
         amountFiat.setCurrency(Optional.<Currency>absent());
       }
     }

     return amountFiat;
  }

  private FiatPayment calculateFiatPaymentAndAddTransactionInfo(Coin amountBTC, String transactionHashAsString) {
    // Get the transactionInfo that contains the fiat exchange info, if it is available from the backing store
    // This will use the fiat rate at time of send/ receive
    TransactionInfo transactionInfo = transactionInfoMap.get(transactionHashAsString);
    if (transactionInfo != null) {
      return transactionInfo.getAmountFiat();
    }

    FiatPayment amountFiat = calculateFiatPaymentEquivalent(amountBTC);

    // Remember the fiat information just worked out
    TransactionInfo newTransactionInfo = new TransactionInfo();
    newTransactionInfo.setHash(transactionHashAsString);
    newTransactionInfo.setAmountFiat(amountFiat);

    // Double check we are not overwriting an extant transactionInfo
    if (transactionInfoMap.get(transactionHashAsString) == null) {
      // Expected
      transactionInfoMap.putIfAbsent(transactionHashAsString, newTransactionInfo);
    }

    return amountFiat;
  }

  private String calculateNote(TransactionData transactionData, String transactionHashAsString) {
    String note = "";

    TransactionInfo transactionInfo = transactionInfoMap.get(transactionHashAsString);
    if (transactionInfo != null) {
      note = transactionInfo.getNote();

      if (note != null) {
        transactionData.setNote(note);
        // if there is a real note use that as the description
        if (note.length() > 0) {
          transactionData.setDescription(note);
        }
      } else {
        transactionData.setNote("");
      }

      transactionData.setAmountFiat(transactionInfo.getAmountFiat());

    } else {
      transactionData.setNote("");
    }

    return note;
  }

  private Optional<Coin> calculateMiningFee(PaymentType paymentType, String transactionHashAsString) {

    Optional<Coin> miningFee = Optional.absent();

    if (paymentType == PaymentType.SENDING || paymentType == PaymentType.SENT) {
      TransactionInfo transactionInfo = transactionInfoMap.get(transactionHashAsString);
      if (transactionInfo != null) {
        miningFee = transactionInfo.getMinerFee();
      }
    }

    return miningFee;
  }

  private Optional<Coin> calculateClientFee(PaymentType paymentType, String transactionHashAsString) {

    Optional<Coin> clientFee = Optional.absent();

    if (paymentType == PaymentType.SENDING || paymentType == PaymentType.SENT) {
      TransactionInfo transactionInfo = transactionInfoMap.get(transactionHashAsString);
      if (transactionInfo != null) {
        clientFee = transactionInfo.getClientFee();
        if (clientFee == null) {
          clientFee = Optional.absent();
        }
      }
    }

    return clientFee;
  }

  /**
   * <p>Populate the internal cache of Payments from the backing store</p>
   */
  public void readPayments() throws PaymentsLoadException {

    Preconditions.checkNotNull(backingStoreFile, "There is no backingStoreFile. Please initialise WalletService.");

    try {
      log.debug("Reading payments from\n'{}'", backingStoreFile.getAbsolutePath());

      ByteArrayInputStream decryptedInputStream = EncryptedFileReaderWriter.readAndDecrypt(
              backingStoreFile,
              WalletManager.INSTANCE.getCurrentWalletSummary().get().getWalletPassword().getPassword(),
              WalletManager.scryptSalt(),
              WalletManager.aesInitialisationVector());
      Payments payments = protobufSerializer.readPayments(decryptedInputStream);

      // For quick access payment requests and transaction infos are stored in maps
      Collection<MBHDPaymentRequestData> MBHDPaymentRequestDatas = payments.getMBHDPaymentRequestDatas();
      MBHDPaymentRequestMap.clear();
      if (MBHDPaymentRequestDatas != null) {
        for (MBHDPaymentRequestData MBHDPaymentRequestData : MBHDPaymentRequestDatas) {
          MBHDPaymentRequestMap.put(MBHDPaymentRequestData.getAddress(), MBHDPaymentRequestData);
        }
      }

      Collection<TransactionInfo> transactionInfos = payments.getTransactionInfos();
      transactionInfoMap.clear();
      if (transactionInfos != null) {
        for (TransactionInfo transactionInfo : transactionInfos) {
          transactionInfoMap.put(transactionInfo.getHash(), transactionInfo);
        }
      }

      Optional<WalletSummary>  walletSummaryOptional = WalletManager.INSTANCE.getCurrentWalletSummary();

      Collection<PaymentRequestData> paymentRequestDatas = payments.getPaymentRequestDatas();
      paymentRequestDataMap.clear();
      if (paymentRequestDatas != null) {
        for (PaymentRequestData paymentRequestData : paymentRequestDatas) {

          // Clear any tx hash if the tx is not in the wallet
          // (See issue https://github.com/bitcoin-solutions/multibit-hd/issues/463)
          // This will get persisted at MBHD close or when payments is next written
          Optional<Sha256Hash> transactionHashOptional = paymentRequestData.getTransactionHashOptional();
          if (transactionHashOptional.isPresent() && walletSummaryOptional.isPresent()) {
            Wallet wallet = walletSummaryOptional.get().getWallet();
            if (wallet != null && wallet.getTransaction(transactionHashOptional.get()) == null) {
              // Transaction is not in the wallet - clear it from the paymentRequesData
              paymentRequestData.setTransactionHashOptional(Optional.<Sha256Hash>absent());
            }
          }

          paymentRequestDataMap.put(paymentRequestData.getUuid(), paymentRequestData);
        }
      }

      readPaymentRequestsFromFiles(paymentRequestDataMap.values(), backingStoreFile);

      log.debug("Reading payments completed\nTransaction infos: {}\nMBHD payment requests: {}\nBIP70 payment requests: {}",
              transactionInfoMap.values().size(), MBHDPaymentRequestMap.values().size(), paymentRequestDataMap.values().size() );

    } catch (EncryptedFileReaderWriterException e) {
      ExceptionHandler.handleThrowable(new PaymentsLoadException("Could not load payments db '" + backingStoreFile.getAbsolutePath() + "'. Error was '" + e.getMessage() + "'."));
    }
  }

  /**
   * <p>Save the payments data to the backing store</p>
   */
  public void writePayments() throws PaymentsSaveException {
    Preconditions.checkNotNull(backingStoreFile, "'backingStoreFile' must be present. Initialise WalletService.");
    Preconditions.checkState(WalletManager.INSTANCE.getCurrentWalletSummary().isPresent(), "Current wallet summary must be present");

    try {
      log.debug("Writing payments to\n'{}'", backingStoreFile.getAbsolutePath());
      log.trace("Writing TransactionInfoMap: {}", transactionInfoMap);

      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(1024);
      Payments payments = new Payments();
      payments.setTransactionInfos(transactionInfoMap.values());
      payments.setMBHDPaymentRequestDatas(MBHDPaymentRequestMap.values());
      payments.setPaymentRequestDatas(paymentRequestDataMap.values());
      protobufSerializer.writePayments(payments, byteArrayOutputStream);
      EncryptedFileReaderWriter.encryptAndWrite(
              byteArrayOutputStream.toByteArray(),
              WalletManager.INSTANCE.getCurrentWalletSummary().get().getWalletPassword().getPassword(),
              backingStoreFile
      );

      writePaymentRequestsToFiles(paymentRequestDataMap.values(), backingStoreFile);

      log.debug("Writing payments completed\nTransaction infos: {}\nMBHD payment requests: {}\nBIP70 payment requests: {}",
              transactionInfoMap.values().size(), MBHDPaymentRequestMap.values().size(), paymentRequestDataMap.values().size() );
    } catch (Exception e) {
      log.error("Could not write to payments db\n'{}'", backingStoreFile.getAbsolutePath(), e);
      throw new PaymentsSaveException("Could not write payments db '" + backingStoreFile.getAbsolutePath() + "'. Error was '" + e.getMessage() + "'.", e);
    }
  }

  private void writePaymentRequestsToFiles(Collection<PaymentRequestData> paymentRequestDatas, File backingStoreFile) {
    Preconditions.checkNotNull(paymentRequestDatas);
    Preconditions.checkNotNull(backingStoreFile);

    // Work out the directory the raw BIP70 payment requests get written to.
    File bip70PaymentRequestDirectory = new File(backingStoreFile.getParent() + File.separator + BIP70_PAYMENT_REQUEST_DIRECTORY);
    if (!bip70PaymentRequestDirectory.exists()) {
      if (!bip70PaymentRequestDirectory.mkdir()) {
        log.error("Could not make directory to store BIP70 payment requests");
        return;
      }
    }

    // Write all the payment requests to disk, encrypted with the wallet password
    // Existing files are not overwritten
    for (PaymentRequestData paymentRequestData : paymentRequestDatas) {
      File outputFile = new File(bip70PaymentRequestDirectory.getAbsolutePath() + File.separator + paymentRequestData.getUuid().toString() + BIP70_PAYMENT_REQUEST_SUFFIX);
      if (!outputFile.exists()) {
        if (paymentRequestData.getPaymentRequest() != null) {
          byte[] serialisedBytes = paymentRequestData.getPaymentRequest().toByteArray();
          try {
            EncryptedFileReaderWriter.encryptAndWrite(serialisedBytes,
                    WalletManager.INSTANCE.getCurrentWalletSummary().get().getWalletPassword().getPassword(), outputFile);
            log.debug("Written serialised bytes of unencrypted length {} to output file {}", serialisedBytes.length, outputFile.getAbsolutePath());
          } catch (EncryptedFileReaderWriterException e) {
            log.error("Failed to write BIP70 payment request file with UUID {}, error was {}", paymentRequestData.getUuid(), e);
          }
        }
      }
    }
  }

  private void readPaymentRequestsFromFiles(Collection<PaymentRequestData> paymentRequestDatas, File backingStoreFile) {
     Preconditions.checkNotNull(paymentRequestDatas);
     Preconditions.checkNotNull(backingStoreFile);

     // Work out the directory the raw BIP70 payment requests get written to.
     File bip70PaymentRequestDirectory = new File(backingStoreFile.getParent() + File.separator + BIP70_PAYMENT_REQUEST_DIRECTORY);
     if (!bip70PaymentRequestDirectory.exists()) {
       // Nothing to do
       return;
     }

     // Read all the payment requests from disk.
     for (PaymentRequestData paymentRequestData : paymentRequestDatas) {
       File inputFile = new File(bip70PaymentRequestDirectory.getAbsolutePath() + File.separator + paymentRequestData.getUuid().toString() + BIP70_PAYMENT_REQUEST_SUFFIX);
       if (inputFile.exists()) {
         byte[] serialisedBytes= EncryptedFileReaderWriter.readAndDecryptToByteArray(inputFile,
                 WalletManager.INSTANCE.getCurrentWalletSummary().get().getWalletPassword().getPassword(),
                 WalletManager.scryptSalt(),
                 WalletManager.aesInitialisationVector());
         log.debug("Read serialised bytes of unencrypted length {} from input file {}", serialisedBytes.length, inputFile.getAbsolutePath());

         try {
           Protos.PaymentRequest paymentRequest = Protos.PaymentRequest.parseFrom(serialisedBytes);
           paymentRequestData.setPaymentRequest(paymentRequest);

           try {
             PaymentSession paymentSession = new PaymentSession(paymentRequest);
             // Payment request is ok

             PaymentSessionSummary paymentSessionSummary = PaymentSessionSummary.newPaymentSessionOK(paymentSession);
             paymentRequestData.setPaymentSessionSummaryOptional(Optional.of(paymentSessionSummary));
             log.debug("Successfuly created paymentSessionSummary from paymentRequest");
           } catch (PaymentProtocolException ppe) {
             // Something wrong with the payment session
             // TODO get host name from somewhere
             String hostName = "unknown";
             PaymentSessionSummary paymentSessionSummary = PaymentSessionSummary.newPaymentSessionFromException(ppe, paymentRequest, hostName);
                          paymentRequestData.setPaymentSessionSummaryOptional(Optional.of(paymentSessionSummary));
             paymentRequestData.setPaymentSessionSummaryOptional(Optional.of(paymentSessionSummary));           }
             log.debug("Partial success in creating paymentSessionSummary from paymentRequest");
         } catch (InvalidProtocolBufferException e) {
           log.error("Failed to read BIP70 payment request file {}, error was {}", inputFile.getAbsolutePath(), e);
         }
       }
     }
   }

  public WalletId getWalletId() {
    return walletId;
  }

  public void addMBHDPaymentRequestData(MBHDPaymentRequestData MBHDPaymentRequestData) {
    MBHDPaymentRequestMap.put(MBHDPaymentRequestData.getAddress(), MBHDPaymentRequestData);
  }

  /**
   * Add a PaymentRequestData to the memory store, keyed by UUID.
   * If it has no fiat information - add it in
   *
   * @param paymentRequestData Payment request data to add (or replace if the UUID already exists)
   */
  public void addPaymentRequestData(PaymentRequestData paymentRequestData) {
    if (!paymentRequestData.getAmountFiat().hasData()) {
        paymentRequestData.setAmountFiat(calculateFiatPaymentEquivalent(paymentRequestData.getAmountCoin()));
    }

    paymentRequestDataMap.put(paymentRequestData.getUuid(), paymentRequestData);

    log.debug("PaymentRequestDataMap:\n{}\n", paymentRequestDataMap);
  }

  public void addTransactionInfo(TransactionInfo transactionInfo) {
    transactionInfoMap.put(transactionInfo.getHash(), transactionInfo);
  }

  public TransactionInfo getTransactionInfoByHash(String transactionHashAsString) {
    return transactionInfoMap.get(transactionHashAsString);
  }

  public Optional<PaymentRequestData> getPaymentRequestDataByHash(String transactionHashAsString) {
    for (PaymentRequestData paymentRequestData : paymentRequestDataMap.values()) {
      if (paymentRequestData.getTransactionHashOptional().isPresent() && paymentRequestData.getTransactionHashOptional().get().toString().equals(transactionHashAsString)) {
        return Optional.of(paymentRequestData);
      }
    }
    return Optional.absent();
  }


  public List<MBHDPaymentRequestData> getMBHDPaymentRequestDatas() {
    return Lists.newArrayList(MBHDPaymentRequestMap.values());
  }

  public List<PaymentRequestData> getPaymentRequestDatas() {
    return Lists.newArrayList(paymentRequestDataMap.values());
  }

  /**
   * Create the next receiving address for the wallet.
   * This is either the first key's address in the wallet or is
   * worked out deterministically and uses the lastIndexUsed on the Payments so that each address is unique
   *
   * @param walletPasswordOptional Either: Optional.absent() = just recycle the first address in the wallet or:  credentials of the wallet to which the new private key is added
   * @return Address the next generated address, as a String. The corresponding private key will be added to the wallet
   */
  public String generateNextReceivingAddress(Optional<CharSequence> walletPasswordOptional) {

    Optional<WalletSummary> currentWalletSummary = WalletManager.INSTANCE.getCurrentWalletSummary();
    if (!currentWalletSummary.isPresent()) {
      // No wallet is present
      throw new IllegalStateException("Trying to add a key to a non-existent wallet");
    } else {
      // Create a new address
      if (walletPasswordOptional.isPresent()) {
        ECKey newKey = currentWalletSummary.get().getWallet().freshReceiveKey();
        String address = newKey.toAddress(networkParameters).toString();
        log.debug("Generated fresh receiving address {}", address);
        return address;
      } else {
        // A credentials is required as all wallets are encrypted
        throw new IllegalStateException("No credentials specified");
      }
    }

  }

  /**
   * Find the payment requests that are either partially or fully funded by the transaction specified
   *
   * @param transactionData The transaction data
   * @return The list of payment requests that the transaction data funds
   */
  public List<MBHDPaymentRequestData> findPaymentRequestsThisTransactionFunds(TransactionData transactionData) {

    List<MBHDPaymentRequestData> MBHDPaymentRequestDataList = Lists.newArrayList();

    if (transactionData != null && transactionData.getOutputAddresses() != null) {
      for (Address address : transactionData.getOutputAddresses()) {
        MBHDPaymentRequestData MBHDPaymentRequestData = MBHDPaymentRequestMap.get(address);
        if (MBHDPaymentRequestData != null) {
          // This transaction funds this payment address
          MBHDPaymentRequestDataList.add(MBHDPaymentRequestData);
        }
      }
    }

    return MBHDPaymentRequestDataList;
  }

  /**
   * Delete a payment request
   */
  public void deletePaymentRequest(MBHDPaymentRequestData MBHDPaymentRequestData) {

    undoDeletePaymentRequestStack.push(MBHDPaymentRequestData);
    MBHDPaymentRequestMap.remove(MBHDPaymentRequestData.getAddress());
    writePayments();
  }

  /**
   * Undo the deletion of a payment request
   */
  public void undoDeletePaymentRequest() {

    if (!undoDeletePaymentRequestStack.isEmpty()) {
      MBHDPaymentRequestData deletedMBHDPaymentRequestData = undoDeletePaymentRequestStack.pop();
      addMBHDPaymentRequestData(deletedMBHDPaymentRequestData);
      writePayments();
    }
  }

  /**
   * Export the payments to two CSV files - one for transactions, one for payment requests.
   * Sends a ExportPerformedEvent with the results.
   *
   * @param exportDirectory        The directory to export to
   * @param transactionFileStem    The stem of the export file for the transactions (will be suffixed with a file suffix and possibly a bracketed number for uniqueness)
   * @param paymentRequestFileStem The stem of the export file for the payment requests (will be suffixed with a file suffix and possibly a bracketed number for uniqueness)
   */
  public void exportPayments(
          File exportDirectory,
          String transactionFileStem,
          String paymentRequestFileStem,
          CSVEntryConverter<MBHDPaymentRequestData> paymentRequestHeaderConverter,
          CSVEntryConverter<MBHDPaymentRequestData> paymentRequestConverter,
          CSVEntryConverter<TransactionData> transactionHeaderConverter,
          CSVEntryConverter<TransactionData> transactionConverter
  ) {
    // Refresh all payments
    Set<PaymentData> paymentDataSet = getPaymentDataSet();
    ExportManager.export(
            paymentDataSet,
            getMBHDPaymentRequestDatas(),
            exportDirectory,
            transactionFileStem,
            paymentRequestFileStem,
            paymentRequestHeaderConverter,
            paymentRequestConverter,
            transactionHeaderConverter,
            transactionConverter
    );
  }

  /**
   * Change the wallet credentials.
   * The result of the operation is emitted as a ChangePasswordResultEvent
   *
   * @param walletSummary The walletsummary with the wallet whose credentials to change
   * @param oldPassword   The old wallet credentials
   * @param newPassword   The new wallet credentials
   */
  public static void changeWalletPassword(final WalletSummary walletSummary, final String oldPassword, final String newPassword) {

    executorService.submit(
            new Runnable() {
              @Override
              public void run() {
                WalletService.changeWalletPasswordInternal(walletSummary, oldPassword, newPassword);
              }
            });
  }

  static void changeWalletPasswordInternal(final WalletSummary walletSummary, final String oldPassword, final String newPassword) {

    if (walletSummary.getWallet() != null) {

      Wallet wallet = walletSummary.getWallet();
      WalletId walletId = walletSummary.getWalletId();

      // Check old credentials
      if (!walletSummary.getWallet().checkPassword(oldPassword)) {
        CoreEvents.fireChangePasswordResultEvent(new ChangePasswordResultEvent(false, CoreMessageKey.CHANGE_PASSWORD_WRONG_OLD_PASSWORD, null));
        return;
      }

      try {
        // Decrypt the seedDerivedAESKey using the old credentials and encrypt it with the new one
        byte[] encryptedOldBackupAESKey = walletSummary.getEncryptedBackupKey();

        KeyParameter oldWalletPasswordDerivedAESKey = org.multibit.hd.core.crypto.AESUtils.createAESKey(oldPassword.getBytes(Charsets.UTF_8), WalletManager.scryptSalt());
        byte[] decryptedOldBackupAESKey = org.multibit.hd.brit.crypto.AESUtils.decrypt(
                encryptedOldBackupAESKey,
                oldWalletPasswordDerivedAESKey,
                WalletManager.aesInitialisationVector());

        KeyParameter newWalletPasswordDerivedAESKey = org.multibit.hd.core.crypto.AESUtils.createAESKey(newPassword.getBytes(Charsets.UTF_8), WalletManager.scryptSalt());
        byte[] encryptedNewBackupAESKey = org.multibit.hd.brit.crypto.AESUtils.encrypt(
                decryptedOldBackupAESKey,
                newWalletPasswordDerivedAESKey,
                WalletManager.aesInitialisationVector());

        // Check the encryption is reversible
        byte[] decryptedRebornBackupAESKey = org.multibit.hd.brit.crypto.AESUtils.decrypt(
                encryptedNewBackupAESKey,
                newWalletPasswordDerivedAESKey,
                WalletManager.aesInitialisationVector());

        if (!Arrays.equals(decryptedOldBackupAESKey, decryptedRebornBackupAESKey)) {
          throw new IllegalStateException("The encryption of the backup AES key was not reversible. Aborting change of wallet credentials");
        }

        // Encrypt the new credentials with an the decryptedOldBackupAESKey
        // Pad the new credentials
        byte[] newPasswordBytes = newPassword.getBytes(Charsets.UTF_8);
        byte[] paddedNewPassword = WalletManager.padPasswordBytes(newPasswordBytes);
        byte[] encryptedPaddedNewPassword = org.multibit.hd.brit.crypto.AESUtils.encrypt(
                paddedNewPassword,
                new KeyParameter(decryptedOldBackupAESKey),
                WalletManager.aesInitialisationVector());

        // Check the encryption is reversible
        byte[] decryptedRebornPaddedNewPassword = org.multibit.hd.brit.crypto.AESUtils.decrypt(
                encryptedPaddedNewPassword,
                new KeyParameter(decryptedOldBackupAESKey),
                WalletManager.aesInitialisationVector());

        if (!Arrays.equals(newPasswordBytes, WalletManager.unpadPasswordBytes(decryptedRebornPaddedNewPassword))) {
          throw new IllegalStateException("The encryption of the new credentials was not reversible. Aborting change of wallet credentials");
        }

        // Locate the installation directory
        File applicationDataDirectory = InstallationManager.getOrCreateApplicationDataDirectory();

        // Load up all the history, contacts and payments using the old credentials
        ContactService contactService = CoreServices.getOrCreateContactService(walletId);
        HistoryService historyService = CoreServices.getOrCreateHistoryService(walletSummary.getWalletPassword());
        WalletService walletService = CoreServices.getOrCreateWalletService(walletId);

        // Change the credentials used to encrypt the wallet
        wallet.decrypt(oldPassword);
        walletSummary.setWalletPassword(new WalletPassword(newPassword, walletSummary.getWalletId()));
        walletSummary.setEncryptedBackupKey(encryptedNewBackupAESKey);
        walletSummary.setEncryptedPassword(encryptedPaddedNewPassword);

        // Save the wallet summary file
        WalletManager.updateWalletSummary(WalletManager.INSTANCE.getCurrentWalletSummaryFile(applicationDataDirectory).get(), walletSummary);

        // Save all the Contacts, history and payment information using the new wallet credentials
        contactService.writeContacts();
        historyService.writeHistory();
        walletService.writePayments();

        wallet.encrypt(newPassword);

        CoreEvents.fireChangePasswordResultEvent(new ChangePasswordResultEvent(true, CoreMessageKey.CHANGE_PASSWORD_SUCCESS, null));
      } catch (RuntimeException | NoSuchAlgorithmException e) {
        log.error("Failed to change password", e);
        CoreEvents.fireChangePasswordResultEvent(new ChangePasswordResultEvent(false, CoreMessageKey.CHANGE_PASSWORD_ERROR, new Object[]{e.getMessage()}));
      }
    } else {
      // No wallet to change the credentials for
      CoreEvents.fireChangePasswordResultEvent(new ChangePasswordResultEvent(false, CoreMessageKey.CHANGE_PASSWORD_ERROR, new Object[]{"There is no wallet"}));
    }
  }


  /**
   * <p>When a transaction is seen by the network, ensure there is a transaction info available storing the exchange rate</p>
   *
   * @param transactionSeenEvent The event (very high frequency during synchronisation)
   */
  @Subscribe
  public void onTransactionSeenEvent(TransactionSeenEvent transactionSeenEvent) {

    // If not in the transaction info map create on and add
    if (transactionInfoMap.get(transactionSeenEvent.getTransactionId()) == null) {

      // Create a new transaction info
      TransactionInfo transactionInfo = new TransactionInfo();
      transactionInfo.setHash(transactionSeenEvent.getTransactionId());

      // Create the fiat payment
      FiatPayment amountFiat = new FiatPayment();
      amountFiat.setExchangeName(Optional.of(ExchangeKey.current().getExchangeName()));

      if (CoreServices.getApplicationEventService() != null) {
        Optional<ExchangeRateChangedEvent> exchangeRateChangedEvent = CoreServices.getApplicationEventService().getLatestExchangeRateChangedEvent();
        if (exchangeRateChangedEvent.isPresent() && exchangeRateChangedEvent.get().getRate() != null) {

          amountFiat.setRate(Optional.of(exchangeRateChangedEvent.get().getRate().toString()));
          BigDecimal localAmount = Coins.toLocalAmount(
                  transactionSeenEvent.getAmount(),
                  exchangeRateChangedEvent.get().getRate()
          );

          if (localAmount.compareTo(BigDecimal.ZERO) != 0) {
            amountFiat.setAmount(Optional.of(localAmount));
          } else {
            amountFiat.setAmount(Optional.<BigDecimal>absent());
          }

          amountFiat.setCurrency(Optional.of(exchangeRateChangedEvent.get().getCurrency()));

        } else {
          amountFiat.setRate(Optional.<String>absent());
          amountFiat.setAmount(Optional.<BigDecimal>absent());
          amountFiat.setCurrency(Optional.<Currency>absent());
        }

        transactionInfo.setAmountFiat(amountFiat);

        // Use the atomic putIfAbsent to ensure we don't overwrite
        if (transactionInfoMap.putIfAbsent(transactionSeenEvent.getTransactionId(), transactionInfo) == null) {
          log.debug("Created TransactionInfo: {}", transactionInfo);
        } else {
          log.debug("Not adding transactionInfo - another process has already added transactionInfo: {}", transactionInfo);
        }
      }
    }
  }

  static class PaymentComparator implements Comparator<PaymentData>, Serializable {
    @Override
    public int compare(PaymentData o1, PaymentData o2) {
      if (o1.getDate() == null ) return -1;
      if (o2.getDate() == null ) return 1;
      int dateSort = o2.getDate().compareTo(o1.getDate()); // note inverse sort
      if (dateSort != 0) {
        return dateSort;
      } else {
        return o1.getAmountCoin().compareTo(o2.getAmountCoin());
      }
    }
  }
}
