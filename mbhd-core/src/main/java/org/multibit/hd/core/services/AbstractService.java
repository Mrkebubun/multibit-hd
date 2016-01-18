package org.multibit.hd.core.services;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import org.multibit.commons.concurrent.SafeExecutors;
import org.multibit.hd.core.events.CoreEvents;
import org.multibit.hd.core.events.ShutdownEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>Abstract base class to provide the following to application services:</p>
 * <ul>
 * <li>Common methods</li>
 * </ul>
 *
 * @since 0.0.1
 *  
 */
public abstract class AbstractService implements ManagedService {

  protected static final Logger log = LoggerFactory.getLogger(AbstractService.class);

  /**
   * The optional scheduled executor service running this service's activity
   */
  private Optional<ListeningScheduledExecutorService> scheduledService = Optional.absent();

  /**
   * The optional executor service running this service's activity
   */
  private Optional<ListeningExecutorService> service = Optional.absent();

  /**
   * Keep track of the class instance to assist debugging
   */
  private final String serviceName;

  protected AbstractService() {

    // Use the standard Object.toString() representation in place of anything else
    serviceName = getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());

    CoreEvents.subscribe(this);

  }

  /**
   * <p>Unsubscribe from events due to the service shutting down</p>
   */
  public void unsubscribe() {
    CoreEvents.unsubscribe(this);
  }

  // Declared final to ensure consistent behaviour across services
  @Override
  public final boolean start() {

    log.info("Service {} starting...", serviceName);

    // Hand over to the specific implementation
    return startInternal();

  }

  /**
   * <p>Continue the service-specific startup code</p>
   * <p>Implementers can rely on the service starting cleanly</p>
   *
   * @return True if the service started successfully
   */
  protected abstract boolean startInternal();

  // Declared final to ensure consistent behaviour across services
  @Override
  public final void shutdownNow(ShutdownEvent.ShutdownType shutdownType) {

    log.info("Service {} shutdown requested...", serviceName);

    if (shutdownNowInternal(shutdownType)) {

      log.debug("Service {} stopping executors...", serviceName);

      if (scheduledService.isPresent()) {
        scheduledService.get().shutdownNow();
      }

      if (service.isPresent()) {
        service.get().shutdownNow();
      }

      // Unsubscribe from events
      unsubscribe();

      log.debug("Service {} stopped", serviceName);
    } else {
      log.info("Service {} shutdown aborted", serviceName);
    }

  }

  /**
   * <p>Continue the service-specific shutdown code</p>
   *
   * @param shutdownType The shutdown type to provide context
   *
   * @return True if the provided executor services should be shutdown and the service unregistered from UI events
   */
  protected abstract boolean shutdownNowInternal(ShutdownEvent.ShutdownType shutdownType);

  /**
   * <p>Convenience method for services to indicate that a "switch" shutdown will not
   * need to shutdown its provided services or to be unregistered from the event bus.</p>
   *
   * <p>Called within the <code>shutdownNowInternal</code> implementation.</p>
   *
   * @param shutdownType The shutdown type to provide context
   *
   * @return False to prevent ongoing cleanup, true if otherwise
   */
  protected boolean preventCleanupOnSwitch(ShutdownEvent.ShutdownType shutdownType) {

    switch (shutdownType) {

      case HARD:
      case SOFT:
        // Allow ongoing cleanup
        return true;
      case SWITCH:
        // Avoid ongoing cleanup
        return false;
      default:
        throw new IllegalStateException("Unsupported state: " + shutdownType.name());
    }
  }

  /**
   * <p>Provide a single thread scheduled executor</p>
   *
   * @param poolName The thread pool name (use lowercase hyphenated)
   */
  protected void requireSingleThreadScheduledExecutor(String poolName) {
    scheduledService = Optional.of(SafeExecutors.newSingleThreadScheduledExecutor(poolName));
  }

  /**
   * <p>Provide a fixed thread pool executor</p>
   *
   * @param threadCount The number of threads
   * @param poolName    The thread pool name (use lowercase hyphenated)
   */
  protected void requireFixedThreadPoolExecutor(int threadCount, String poolName) {
    service = Optional.of(SafeExecutors.newFixedThreadPool(threadCount, poolName));
  }

  /**
   * @return The executor service
   */
  protected ListeningExecutorService getExecutorService() {
    return service.get();
  }

  /**
   * @return The executor service
   */
  protected ListeningScheduledExecutorService getScheduledExecutorService() {
    return scheduledService.get();
  }
}
