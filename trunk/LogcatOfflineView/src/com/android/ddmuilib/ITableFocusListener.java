/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ddmuilib;

import org.eclipse.swt.dnd.Clipboard;

/**
 * An object listening to focus change in Table objects.<br>
 * For application not relying on a RCP to provide menu changes based on focus,
 * this class allows to get monitor the focus change of several Table widget
 * and update the menu action accordingly.
 */
public interface ITableFocusListener {

    public interface IFocusedTableActivator {
        public void copy(Clipboard clipboard);

        public void selectAll();
        public void previous();
        public void next();
    }

    public void focusGained(IFocusedTableActivator activator);

    public void focusLost(IFocusedTableActivator activator);
}
