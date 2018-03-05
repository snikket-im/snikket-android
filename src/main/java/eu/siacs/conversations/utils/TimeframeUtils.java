/*
 * Copyright (c) 2018, Daniel Gultsch All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package eu.siacs.conversations.utils;

import android.content.Context;
import android.support.annotation.PluralsRes;

import eu.siacs.conversations.R;

public class TimeframeUtils {

	private static final Timeframe[] TIMEFRAMES;

	static {
		TIMEFRAMES = new Timeframe[]{
				new Timeframe(1000L, R.plurals.seconds),
				new Timeframe(60L * 1000, R.plurals.minutes),
				new Timeframe(60L * 60 * 1000, R.plurals.hours),
				new Timeframe(24L * 60 * 60 * 1000, R.plurals.days),
				new Timeframe(7L * 24 * 60 * 60 * 1000, R.plurals.weeks),
				new Timeframe(30L * 24 * 60 * 60 * 1000, R.plurals.months),
		};
	}

	public static String resolve(Context context, long timeframe) {
		for(int i = TIMEFRAMES.length -1 ; i >= 0; --i) {
			long duration = TIMEFRAMES[i].duration;
			long threshold = i > 0 ? (TIMEFRAMES[i-1].duration / 2) : 0;
			if (timeframe >= duration - threshold) {
				int count = (int) (timeframe / duration + ((timeframe%duration)>(duration/2)?1:0));
				return context.getResources().getQuantityString(TIMEFRAMES[i].name,count,count);
			}
		}
		return context.getResources().getQuantityString(TIMEFRAMES[0].name,0,0);
	}


	private static class Timeframe {
		public final long duration;
		public final int name;

		private Timeframe(long duration, @PluralsRes int name) {
			this.duration = duration;
			this.name = name;
		}
	}

}