/**
 * Copyright 2011 multibit.org
 *
 * Licensed under the MIT license (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://opensource.org/licenses/mit-license.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.multibit.hd.ui.platform.builder.mac;

import org.multibit.hd.ui.platform.GenericApplication;
import org.multibit.hd.ui.platform.handler.*;
import org.multibit.hd.ui.platform.listener.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

/**
 * <p>GenericApplication to provide the following to application:</p>
 * <ul>
 * <li>Provision of Apple Mac specific implementations of common methods</li>
 * </ul>
 *
 * @see <a href="http://developer.apple.com/library/mac/documentation/Java/Reference/JavaSE6_AppleExtensionsRef/api/index.html?com/apple/eawt/Application.html">The Apple EAWT Javadocs</a>
 * @since 0.3.0
 *         
 */
public class MacApplication implements GenericApplication {

  private static final Logger log = LoggerFactory.getLogger(MacApplication.class);

  /**
   * The native EAWT Application instance providing OS events
   */
  private Object nativeApplication;

  /**
   * Handles the OpenURI use case
   */
  private Class nativeOpenURIHandlerClass;
  /**
   * Handles the Open Files use case
   */
  private Class nativeOpenFilesHandlerClass;
  /**
   * Handles the Preferences use case
   */
  private Class nativePreferencesHandlerClass;
  /**
   * Handles the About use case
   */
  private Class nativeAboutHandlerClass;
  /**
   * Handles the Quit use case
   */
  private Class nativeQuitHandlerClass;

  public void addOpenURIHandler(GenericOpenURIHandler openURIHandler) {

    log.trace("Adding GenericOpenURIHandler");
    // Ensure the implementing class is public
    // This avoids anonymous interface issues
    if (!Modifier.isPublic(openURIHandler.getClass().getModifiers())) {
      throw new IllegalArgumentException("GenericOpenURIHandler must be a public class");
    }

    // Load up an instance of the native OpenURIHandler
    // Provide an invocation handler to link the native openURI(AppEvent.OpenURIEvent event)
    // back to the generic handler
    Object nativeOpenURIHandler = Proxy.newProxyInstance(getClass().getClassLoader(),
      new Class[]{nativeOpenURIHandlerClass},
      new OpenURIHandlerInvocationHandler(openURIHandler, GenericOpenURIEvent.class));

    // Reflective call as application.setOpenURIHandler(nativeOpenURIHandler)
    // nativeOpenURIHandler is a proxy that actually uses the generic handler
    callNativeMethod(nativeApplication, "setOpenURIHandler", new Class[]{nativeOpenURIHandlerClass}, new Object[]{nativeOpenURIHandler});

    log.trace("GenericOpenURIHandler configured");

  }

  public void addOpenFilesHandler(GenericOpenFilesHandler openFilesHandler) {

    log.trace("Adding GenericOpenFilesHandler");
    // Ensure the implementing class is public
    // This avoids anonymous interface issues
    if (!Modifier.isPublic(openFilesHandler.getClass().getModifiers())) {
      throw new IllegalArgumentException("GenericOpenFilesHandler must be a public class");
    }

    // Load up an instance of the native OpenFilesHandler
    // Provide an invocation handler to link the native openURI(AppEvent.OpenFilesEvent event)
    // back to the generic handler
    Object nativeOpenFilesHandler = Proxy.newProxyInstance(getClass().getClassLoader(),
      new Class[]{nativeOpenFilesHandlerClass},
      new OpenFilesHandlerInvocationHandler(openFilesHandler, GenericOpenFilesEvent.class));

    // Reflective call as application.setOpenFileHandler(nativeOpenFilesHandler)
    // (note inconsistent singular)
    // nativeOpenFilesHandler is a proxy that actually uses the generic handler
    callNativeMethod(nativeApplication, "setOpenFileHandler", new Class[]{nativeOpenFilesHandlerClass}, new Object[]{nativeOpenFilesHandler});

    log.trace("GenericOpenFilesHandler configured");

  }

  public void addPreferencesHandler(GenericPreferencesHandler preferencesHandler) {

    log.trace("Adding GenericPreferencesHandler");
    // Ensure the implementing class is public
    // This avoids anonymous interface issues
    if (!Modifier.isPublic(preferencesHandler.getClass().getModifiers())) {
      throw new IllegalArgumentException("GenericPreferencesHandler must be a public class");
    }

    // Load up an instance of the native PreferencesHandler
    // Provide an invocation handler to link the native preferences(AppEvent.PreferencesEvent event)
    // back to the generic handler
    Object nativePreferencesHandler = Proxy.newProxyInstance(getClass().getClassLoader(),
      new Class[]{nativePreferencesHandlerClass},
      new PreferencesHandlerInvocationHandler(preferencesHandler, GenericPreferencesEvent.class));

    // Reflective call as application.setPreferencesHandler(nativePreferencesHandler)
    // nativePreferencesHandler is a proxy that actually uses the generic handler
    callNativeMethod(nativeApplication, "setPreferencesHandler", new Class[]{nativePreferencesHandlerClass}, new Object[]{nativePreferencesHandler});

    log.trace("GenericPreferencesHandler configured");

  }

  public void addAboutHandler(GenericAboutHandler aboutHandler) {

    log.trace("Adding GenericAboutHandler");
    // Ensure the implementing class is public
    // This avoids anonymous interface issues
    if (!Modifier.isPublic(aboutHandler.getClass().getModifiers())) {
      throw new IllegalArgumentException("GenericAboutHandler must be a public class");
    }

    // Load up an instance of the native AboutHandler
    // Provide an invocation handler to link the native about(AppEvent.AboutEvent event)
    // back to the generic handler
    Object nativeAboutHandler = Proxy.newProxyInstance(getClass().getClassLoader(),
      new Class[]{nativeAboutHandlerClass},
      new AboutHandlerInvocationHandler(aboutHandler, GenericAboutEvent.class));

    // Reflective call as application.setAboutHandler(nativeAboutHandler)
    // nativeAboutHandler is a proxy that actually uses the generic handler
    callNativeMethod(nativeApplication, "setAboutHandler", new Class[]{nativeAboutHandlerClass}, new Object[]{nativeAboutHandler});

    log.trace("GenericAboutHandler configured");

  }

  public void addQuitHandler(GenericQuitHandler quitHandler) {

    log.trace("Adding GenericQuitHandler");
    // Ensure the implementing class is public
    // This avoids anonymous interface issues
    if (!Modifier.isPublic(quitHandler.getClass().getModifiers())) {
      throw new IllegalArgumentException("GenericQuitHandler must be a public class");
    }

    // Load up an instance of the native QuitHandler
    // Provide an invocation handler to link the native about(AppEvent.AboutEvent event)
    // back to the generic handler
    Object nativeQuitHandler = Proxy.newProxyInstance(getClass().getClassLoader(),
      new Class[]{nativeQuitHandlerClass},
      new QuitHandlerInvocationHandler(quitHandler, GenericQuitEvent.class, GenericQuitResponse.class));

    // Reflective call as application.setQuitHandler(nativeQuitHandler)
    // nativeQuitHandler is a proxy that actually uses the generic handler
    callNativeMethod(nativeApplication, "setQuitHandler", new Class[]{nativeQuitHandlerClass}, new Object[]{nativeQuitHandler});

    log.trace("GenericQuitHandler configured");

  }

  /**
   * Calls a non-zero argument method of the given (usually native) object
   *
   * @param object     The object
   * @param methodName The method name
   * @param classes    The classes of the arguments in the order they appear in the method signature
   * @param arguments  The values of the arguments in the order they appear in the method signature
   *
   * @return The result of the call
   */
  private Object callNativeMethod(Object object, String methodName, Class[] classes, Object[] arguments) {
    log.trace("Calling methodName {}", methodName);
    try {
      // Build a suitable Class[] for the method signature based on the arguments
      if (classes == null) {
        classes = new Class[arguments.length];
        for (int i = 0; i < classes.length; i++) {
          classes[i] = arguments[i].getClass();

        }
      }
      Method method = object.getClass().getMethod(methodName, classes);
      return method.invoke(object, arguments);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean isMac() {
    return true;
  }

  @Override
  public boolean isLinux() {
    return false;
  }

  @Override
  public boolean isWindows() {
    return false;
  }

  public void setApplication(Object application) {
    this.nativeApplication = application;
  }

  public void setOpenURIHandlerClass(Class openURIHandlerClass) {
    this.nativeOpenURIHandlerClass = openURIHandlerClass;
  }

  public void setOpenFilesHandlerClass(Class openFilesHandlerClass) {
    this.nativeOpenFilesHandlerClass = openFilesHandlerClass;
  }

  public void setPreferencesHandlerClass(Class preferencesHandlerClass) {
    this.nativePreferencesHandlerClass = preferencesHandlerClass;
  }

  public void setAboutHandlerClass(Class aboutHandlerClass) {
    this.nativeAboutHandlerClass = aboutHandlerClass;
  }

  public void setQuitHandlerClass(Class quitHandlerClass) {
    this.nativeQuitHandlerClass = quitHandlerClass;
  }
}


