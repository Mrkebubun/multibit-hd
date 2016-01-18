package org.multibit.hd.core.dto;

import com.google.common.base.Optional;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Wallet;
import org.multibit.hd.brit.core.dto.FeeState;
import org.spongycastle.crypto.params.KeyParameter;

/**
 * <p>Data object to provide the following to Bitcoin network service:</p>
 * <ul>
 * <li>Contains send bitcoin data</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class SendRequestSummary {

  private final Address destinationAddress;
  private final Optional<FiatPayment> fiatPayment;
  private final Coin feePerKB;
  private final Optional<FeeState> feeState;

  // Mutable values
  private boolean emptyWallet;
  private Coin amount;
  private Optional<KeyParameter> keyParameter = Optional.absent();
  private Optional<Wallet.SendRequest> sendRequest = Optional.absent();
  private Optional<String> notes = Optional.absent();
  private String password;
  private boolean applyClientFee = false;
  private Address changeAddress;

  /**
   * The client fee added to the sendRequest.tx
   */
  private Optional<Coin> clientFeeAdded = Optional.absent();

  /**
   * @param destinationAddress The destination address to send to
   * @param amount             The amount to send (in coins)
   * @param fiatPayment        the fiat payment equivalent of the bitcoin amount
   *                           Note that initially, this is filled up with only the exchange rate details.
   *                           Only when bitcoin is sent are the fees and hence total bitcoin amount worked out.
   *                           Then the fiat amount equivalent to the total bitcoin amount is computed and stored.
   * @param changeAddress      The change address
   * @param feePerKB           The fee per Kb (in coins)
   * @param password           The wallet credentials
   * @param feeState           The BRIT fee state
   * @param emptyWallet        True if the wallet should be fully emptied including all payable fees
   */
  public SendRequestSummary(
    Address destinationAddress,
    Coin amount,
    Optional<FiatPayment> fiatPayment,
    Address changeAddress,
    Coin feePerKB,
    String password,
    Optional<FeeState> feeState,
    boolean emptyWallet) {

    this.destinationAddress = destinationAddress;
    this.amount = amount;
    this.fiatPayment = fiatPayment;
    this.changeAddress = changeAddress;
    this.feePerKB = feePerKB;
    this.password = password;
    this.feeState = feeState;
    this.emptyWallet = emptyWallet;
  }

  /**
   * @param sendRequest The send request (usually from a PaymentSession)
   * @param fiatPayment The fiat payment equivalent of the bitcoin amount
   *                    Note that initially, this is filled up with only the exchange rate details.
   *                    Only when bitcoin is sent are the fees and hence total bitcoin amount worked out.
   *                    Then the fiat amount equivalent to the total bitcoin amount is computed and stored.
   * @param feePerKB    The fee per Kb (in coins)
   * @param password    The wallet credentials
   * @param feeState    The BRIT fee state
   */
  public SendRequestSummary(Wallet.SendRequest sendRequest, Optional<FiatPayment> fiatPayment, Coin feePerKB, String password, Optional<FeeState> feeState) {
    this.destinationAddress = null;
    this.changeAddress = null;
    this.sendRequest = Optional.of(sendRequest);
    this.fiatPayment = fiatPayment;
    this.feePerKB = feePerKB;
    this.password = password;
    this.feeState = feeState;
    this.emptyWallet = false;
  }

  /**
   * @return The destination address
   */
  public Address getDestinationAddress() {
    return destinationAddress;
  }

  /**
   * @return The amount sent by the user in coins
   * This does NOT include any client fee
   */
  public Coin getAmount() {
    return amount;
  }

  public void setAmount(Coin recipientAmount) {
    amount = recipientAmount;
  }

  /**
   * @return The total amount sent, including the client fee
   */
  public Coin getTotalAmount() {
    if (clientFeeAdded.isPresent()) {
      return amount.add(clientFeeAdded.get());
    } else {
      return amount;
    }
  }

  /**
   * Get the fiat payment of the total amount being sent
   */
  public Optional<FiatPayment> getFiatPayment() {
    return fiatPayment;
  }

  /**
   * @return The change address
   */
  public Address getChangeAddress() {
    return changeAddress;
  }

  public void setChangeAddress(Address changeAddress) {
    this.changeAddress = changeAddress;
  }

  /**
   * @return The fee to pay per Kb
   */
  public Coin getFeePerKB() {
    return feePerKB;
  }

  /**
   * @return The wallet credentials
   */
  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  /**
   * @return The fee state to determine if a client fee is required
   */
  public Optional<FeeState> getFeeState() {
    return feeState;
  }

  /**
   * @return True if the wallet should be fully emptied including all payable fees
   */
  public boolean isEmptyWallet() {
    return emptyWallet;
  }

  public void setEmptyWallet(boolean emptyWallet) {
    this.emptyWallet = emptyWallet;
  }

  /**
   * @return The wallet AES key
   */
  public Optional<KeyParameter> getKeyParameter() {
    return keyParameter;
  }

  public void setKeyParameter(KeyParameter keyParameter) {
    this.keyParameter = Optional.fromNullable(keyParameter);
  }

  /**
   * @return The Bitcoinj send request providing detailed information about the transaction
   */
  public Optional<Wallet.SendRequest> getSendRequest() {
    return sendRequest;
  }

  public void setSendRequest(Wallet.SendRequest sendRequest) {
    this.sendRequest = Optional.fromNullable(sendRequest);
  }

  public void setNotes(Optional<String> notes) {
    this.notes = notes;
  }

  public Optional<String> getNotes() {
    return notes;
  }


  public Optional<Coin> getClientFeeAdded() {
    return clientFeeAdded;
  }

  public void setClientFeeAdded(Optional<Coin> clientFeeAdded) {
    this.clientFeeAdded = clientFeeAdded;
  }

  public boolean isApplyClientFee() {
    return applyClientFee;
  }

  public void setApplyClientFee(boolean applyClientFee) {
    this.applyClientFee = applyClientFee;
  }

  @Override
  public String toString() {
    return "SendRequestSummary{" +
      "destinationAddress=" + destinationAddress +
      ", amount=" + amount +
      ", fiatPayment=" + fiatPayment +
      ", changeAddress=" + changeAddress +
      ", feePerKB=" + feePerKB +
      ", credentials=***" +
      ", feeStateOptional=" + feeState +
      ", clientFeeAdded=" + clientFeeAdded +
      ", notes=***" +
      ", applyClientFee= " + applyClientFee +
      '}';
  }
}
