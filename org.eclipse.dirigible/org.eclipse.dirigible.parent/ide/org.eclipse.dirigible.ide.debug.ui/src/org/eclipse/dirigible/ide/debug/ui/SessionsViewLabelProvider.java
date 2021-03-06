/******************************************************************************* 
 * Copyright (c) 2015 SAP and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0 
 * which accompanies this distribution, and is available at 
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   SAP - initial API and implementation
 *******************************************************************************/

package org.eclipse.dirigible.ide.debug.ui;

import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.graphics.Image;

public class SessionsViewLabelProvider extends AbstractDebugLabelProvider implements ITableLabelProvider {
	
	private static final long serialVersionUID = 2684004047978059155L;

	@Override
	public Image getColumnImage(Object element, int columnIndex) {
		return columnIndex == 0 ? getImage() : null;
	}

	public Image getImage() {
		return createImage(DEBUG_SESSION_ICON_URL);
	}

	@Override
	public String getColumnText(Object element, int columnIndex) {
		if (element instanceof String) {
			return (String) element;
		}
		return "invalid session";
	}

}