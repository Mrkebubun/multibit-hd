package org.multibit.hd.ui.controllers;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.eventbus.Subscribe;
import org.bitcoinj.core.Coin;
import org.multibit.hd.core.config.Configurations;
import org.multibit.hd.core.dto.RAGStatus;
import org.multibit.hd.core.events.ExchangeRateChangedEvent;
import org.multibit.hd.core.events.SlowTransactionSeenEvent;
import org.multibit.hd.core.managers.WalletManager;
import org.multibit.hd.core.services.CoreServices;
import org.multibit.hd.core.utils.Coins;
import org.multibit.hd.ui.audio.Sounds;
import org.multibit.hd.ui.events.controller.AddAlertEvent;
import org.multibit.hd.ui.events.controller.RemoveAlertEvent;
import org.multibit.hd.ui.events.view.ViewEvents;
import org.multibit.hd.ui.models.AlertModel;

import java.math.BigDecimal;
import java.util.List;

/**
 * <p>Controller for the header view </p>
 * <ul>
 * <li>Handles interaction between the model and the view</li>
 * </ul>
 */
public class HeaderController extends AbstractController {

  private final List<AlertModel> alertModels = Lists.newArrayList();

  /**
   * <p>Trigger a refresh of the header view to ensure alert panels are shown</p>
   */
  public void refresh() {

    if (!alertModels.isEmpty()) {

      // The alert structure has changed so inform the view
      ViewEvents.fireAlertAddedEvent(alertModels.get(0));
    }
  }

  /**
   * <p>Called when the exchange rate changes</p>
   *
   * @param event The exchange rate change event
   */
  @Subscribe
  public void onExchangeRateChangedEvent(final ExchangeRateChangedEvent event) {

    // Build the exchange string
    final Optional<Coin> availableCoin = WalletManager.INSTANCE.getCurrentWalletBalance();
    final Optional<Coin> estimatedCoin = WalletManager.INSTANCE.getCurrentWalletBalanceWithUnconfirmed();

    final BigDecimal localBalance;

    if (event.getRate() != null) {
      localBalance = Coins.toLocalAmount(availableCoin.or(Coin.ZERO), event.getRate());
    } else {
      localBalance = null;
    }

    // Post the event
    ViewEvents.fireBalanceChangedEvent(
            availableCoin.or(Coin.ZERO),
            estimatedCoin.or(Coin.ZERO),
            localBalance,
            event.getRateProvider()
    );

  }

  /**
   * <p>Called when there are payments seen that may change the balance</p>
   *
   * @param event The slow transaction seen event
   */
  @Subscribe
  public void onSlowTransactionSeenEvent(SlowTransactionSeenEvent event) {

    Optional<ExchangeRateChangedEvent> exchangeRateChangedEventOptional = CoreServices.getApplicationEventService().getLatestExchangeRateChangedEvent();

    if (exchangeRateChangedEventOptional.isPresent()) {
      onExchangeRateChangedEvent(exchangeRateChangedEventOptional.get());
    } else {
      // No exchange rate available but fire an event anyhow to force a balance change event
      onExchangeRateChangedEvent(new ExchangeRateChangedEvent(null, null, Optional.<String>absent(), null));
    }
  }

  /**
   * <p>Handles the presentation of a new alert</p>
   *
   * @param event The balance change event
   */
  @Subscribe
  public synchronized void onAddAlertEvent(AddAlertEvent event) {

    boolean ignoreRepeated = false;
    // Check for repeated text
    for (AlertModel alertModel : alertModels) {
      if (alertModel.getLocalisedMessage().equals(event.getAlertModel().getLocalisedMessage())) {
        // Ignore duplicated message
        ignoreRepeated = true;
        break;
      }
    }

    if (!ignoreRepeated) {

      // Add this to the list
      alertModels.add(event.getAlertModel());

      // Play a beep on the first alert for RED or AMBER
      RAGStatus severity = event.getAlertModel().getSeverity();
      if (RAGStatus.RED.equals(severity)
              || RAGStatus.AMBER.equals(severity)) {
        Sounds.playBeep(Configurations.currentConfiguration.getSound());
      }

      // Adjust the models to reflect the new M of N values
      updateRemaining();
    }

    // The alert structure has changed so inform the view
    ViewEvents.fireAlertAddedEvent(alertModels.get(0));

  }

  /**
   * <p>Handles the representation of the balance based on the current configuration</p>
   *
   * @param event The balance change event
   */
  @Subscribe
  public synchronized void onRemoveAlertEvent(RemoveAlertEvent event) {

    if (!alertModels.isEmpty()) {
      // Remove the topmost alert model
      alertModels.remove(0);

      // Adjust the models to reflect the new M of N values
      updateRemaining();

      // The alert structure has changed so inform the view
      if (!alertModels.isEmpty()) {
        ViewEvents.fireAlertAddedEvent(alertModels.get(0));
      } else {
        // Use an empty event to signal that the event should be hidden
        ViewEvents.fireAlertRemovedEvent();
      }
    }
  }

  /**
   * <p>Updates the "remaining" values for alert models</p>
   */
  private void updateRemaining() {

    Preconditions.checkNotNull(alertModels, "'alertModels' must be present");

    // Update the "remaining" based on the position in the list
    for (int i = 0; i < alertModels.size(); i++) {
      AlertModel alertModel = alertModels.get(i);

      Preconditions.checkNotNull(alertModel, "'alertModel' must be present");

      alertModel.setRemaining(alertModels.size() - 1);
    }

  }
}
