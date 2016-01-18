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
package org.multibit.hd.ui.platform.handler;

import org.multibit.hd.ui.platform.listener.GenericOpenFilesEvent;

/**
 * <p>Generic handler to provide the following to {@link org.multibit.hd.ui.platform.GenericApplication}:</p>
 * <ul>
 * <li>Proxies any native handling code</li>
 * </ul>
 *
 * @since 0.8.0
 *         
 */
public interface GenericOpenFilesHandler extends GenericHandler {
  /**
   * Called in response to receiving an open files event
   *
   * @param event The generic open files event
   */
  void openFiles(GenericOpenFilesEvent event);
}
