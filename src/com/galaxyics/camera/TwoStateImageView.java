/**************************************************************************/
/* MaclawStudios Camera App for Samsung Galaxy Ace and Gio                */
/* Copyright (C) 2012 Pavel Kirpichyov & Marcin Chojnacki & MaclawStudios */
/*                                                                        */
/* This program is free software: you can redistribute it and/or modify   */
/* it under the terms of the GNU General Public License as published by   */
/* the Free Software Foundation, either version 3 of the License, or      */
/* (at your option) any later version.                                    */
/*                                                                        */
/* This program is distributed in the hope that it will be useful,        */
/* but WITHOUT ANY WARRANTY; without even the implied warranty of         */
/* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the           */
/* GNU General Public License for more details.                           */
/*                                                                        */
/* You should have received a copy of the GNU General Public License      */
/* along with this program.  If not, see <http://www.gnu.org/licenses/>   */
/**************************************************************************/

package com.galaxyics.camera;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * A @{code ImageView} which change the opacity of the icon if disabled.
 */
public class TwoStateImageView extends ImageView {
	private final float DISABLED_ALPHA = 0.4f;
	private boolean mFilterEnabled = true;

	public TwoStateImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public TwoStateImageView(Context context) {
		this(context, null);
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		if (mFilterEnabled) {
			if (enabled) {
				setAlpha(1.0f);
			} else {
				setAlpha(DISABLED_ALPHA);
			}
		}
	}

	public void enableFilter(boolean enabled) {
		mFilterEnabled = enabled;
	}
}