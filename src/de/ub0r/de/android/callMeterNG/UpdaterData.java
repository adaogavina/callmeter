/*
 * Copyright (C) 2010 Felix Bechstein
 * 
 * This file is part of Call Meter NG.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; If not, see <http://www.gnu.org/licenses/>.
 */
package de.ub0r.de.android.callMeterNG;

import java.io.IOException;
import java.util.Calendar;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * AsyncTask to handlel calcualtions/display for data in background.
 * 
 * @author flx
 */
class UpdaterData extends AsyncTask<Void, Void, Long[]> {
	/** Tag for output. */
	private static final String TAG = "CallMeterNG.ud";

	/** Prefs: enable data stats. */
	private static final String PREFS_DATA_ENABLE = "data_enable";
	/** Prefs: bill each date separately. */
	private static final String PREFS_DATA_EACHDAY = "data_eachday";
	/** Prefs: limit for data traffic. */
	private static final String PREFS_DATA_LIMIT = "data_limit";
	/** Prefs: bill only incoming data. */
	private static final String PREFS_DATA_INCOMING_ONLY = "data_incoming_only";

	/** Prefs: data in at last boot. */
	private static final String PREFS_DATA_BOOT_IN = "data_boot_in";
	/** Prefs: data out at last boot. */
	private static final String PREFS_DATA_BOOT_OUT = "data_boot_out";
	/** Prefs: data in after last boot. */
	private static final String PREFS_DATA_RUNNING_IN = "data_running_in";
	/** Prefs: data out after last boot. */
	private static final String PREFS_DATA_RUNNING_OUT = "data_running_out";
	/** Prefs: data in before bolling date. */
	private static final String PREFS_DATA_PREBILLING_IN = "data_prebilling_in";
	/** Prefs: data out before billing date. */
	private static final String PREFS_DATA_PREBILLING_OUT = // .
	"data_prebilling_out";
	/** Prefs: date of last billing. */
	private static final String PREFS_DATA_LASTCHECK = "data_lastcheck";

	/** Byte units. */
	private static final String BYTE_UNITS_B = "B";
	/** Byte units: kB. */
	private static final String BYTE_UNITS_KB = "kB";
	/** Byte units: MB. */
	private static final String BYTE_UNITS_MB = "MB";
	/** Byte units: GB. */
	private static final String BYTE_UNITS_GB = "GB";
	/** Byte units: TB. */
	private static final String BYTE_UNITS_TB = "TB";
	/** Bytes: kB. */
	private static final long BYTE_KB = 1024L;
	/** Bytes: MB. */
	private static final long BYTE_MB = BYTE_KB * BYTE_KB;
	/** Bytes: GB. */
	private static final long BYTE_GB = BYTE_MB * BYTE_KB;
	/** Bytes: TB. */
	private static final long BYTE_TB = BYTE_GB * BYTE_KB;

	/** Status Strings. */
	private String dataIn, dataOut, dataBillDate;
	/** Status TextViews. */
	private TextView twDataBillDate, twDataIn, twDataOut;
	/** Status ProgressBar. */
	private ProgressBar pbData;
	/** Status ProgressBar Text. */
	private TextView twPBDataText;

	/** Preferences to use. */
	private final SharedPreferences prefs;
	/** Context to use. */
	private final Context context;
	/** Ref to CallMeter instance. */
	private final CallMeter callmeter;
	/** Run updates on GUI. */
	private final boolean updateGUI;

	/**
	 * AsyncTask updating statistics.
	 * 
	 * @param c
	 *            {@link Context}
	 */
	UpdaterData(final Context c) {
		this.context = c;
		if (c != null && c instanceof CallMeter) {
			this.updateGUI = true;
			this.callmeter = (CallMeter) c;
		} else {
			this.updateGUI = false;
			this.callmeter = null;
		}
		this.prefs = PreferenceManager.getDefaultSharedPreferences(c);
	}

	/**
	 * Update text on main Activity.
	 */
	private void updateText() {
		if (!this.updateGUI) {
			return;
		}

		this.twDataBillDate.setText(this.dataBillDate);
		this.twDataIn.setText(this.dataIn);
		this.twDataOut.setText(this.dataOut);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onPreExecute() {
		if (this.updateGUI) {
			this.pbData = (ProgressBar) this.callmeter
					.findViewById(R.id.data_progressbar);
			this.twPBDataText = (TextView) this.callmeter
					.findViewById(R.id.data_progressbar_text);
			this.twDataBillDate = (TextView) this.callmeter
					.findViewById(R.id.data_billdate);
			this.twDataIn = (TextView) this.callmeter
					.findViewById(R.id.data_in);
			this.twDataOut = (TextView) this.callmeter
					.findViewById(R.id.data_out);

			int v = View.GONE;
			if (this.prefs.getBoolean(PREFS_DATA_ENABLE, false)) {
				v = View.VISIBLE;
			}
			this.callmeter.findViewById(R.id.data_).setVisibility(v);
			this.callmeter.findViewById(R.id.data_billdate_layout)
					.setVisibility(v);
			this.callmeter.findViewById(R.id.data_in_layout).setVisibility(v);
			this.callmeter.findViewById(R.id.data_out_layout).setVisibility(v);
			this.callmeter.findViewById(R.id.data_progressbar).setVisibility(v);

			this.dataBillDate = "?";
			this.dataIn = "?";
			this.dataOut = "?";

			this.updateText();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Long[] doInBackground(final Void... arg0) {
		// progressbar positions: data_pos, data_max
		final Long[] ret = { 0L, 0L };
		Calendar calBillDate = getBillDate(this.prefs);
		this.dataBillDate = DateFormat.getDateFormat(this.context).format(
				calBillDate.getTime());

		// report data only if run from GUI
		if (this.prefs.getBoolean(PREFS_DATA_ENABLE, false) && this.updateGUI) {
			// walk data
			updateTraffic(this.context, this.prefs);
			// get data from preferences
			final long preBootIn = this.prefs.getLong(PREFS_DATA_BOOT_IN, 0);
			final long preBootOut = this.prefs.getLong(PREFS_DATA_BOOT_OUT, 0);
			final long preBillingIn = this.prefs.getLong(
					PREFS_DATA_PREBILLING_IN, 0);
			final long preBillingOut = this.prefs.getLong(
					PREFS_DATA_PREBILLING_OUT, 0);
			final long runningIn = this.prefs.getLong(PREFS_DATA_RUNNING_IN, 0);
			final long runningOut = this.prefs.getLong(PREFS_DATA_RUNNING_OUT,
					0);
			final long currentIn = preBootIn + runningIn;
			final long currentOut = preBootOut + runningOut;
			final long thisBillingIn = currentIn - preBillingIn;
			final long thisBillingOut = currentOut - preBillingOut;
			this.dataIn = prettyBytes(thisBillingIn) + "/"
					+ prettyBytes(currentIn);
			this.dataOut = prettyBytes(currentOut - preBillingOut) + "/"
					+ prettyBytes(currentOut);
			int limit = 0;
			try {
				limit = Integer.parseInt(this.prefs.getString(PREFS_DATA_LIMIT,
						"0"));
			} catch (NumberFormatException e) {
				Log.e(TAG, null, e);
			}

			ret[0] = thisBillingIn;
			if (!this.prefs.getBoolean(PREFS_DATA_INCOMING_ONLY, false)) {
				ret[0] += thisBillingOut;
			}
			ret[1] = limit * BYTE_MB;
		}

		return ret;
	}

	/**
	 * Return pretty bytes.
	 * 
	 * @author Cyril Jaquier
	 * @param value
	 *            bytes
	 * @return pretty bytes
	 */
	private static String prettyBytes(final long value) {
		StringBuilder sb = new StringBuilder();
		if (value < BYTE_KB) {
			sb.append(String.valueOf(value));
			sb.append(BYTE_UNITS_B);
		} else if (value < BYTE_MB) {
			sb.append(String.format("%.1f", value / (BYTE_KB * 1.0)));
			sb.append(BYTE_UNITS_KB);
		} else if (value < BYTE_GB) {
			sb.append(String.format("%.2f", value / (BYTE_MB * 1.0)));
			sb.append(BYTE_UNITS_MB);
		} else if (value < BYTE_TB) {
			sb.append(String.format("%.3f", value / (BYTE_GB * 1.0)));
			sb.append(BYTE_UNITS_GB);
		} else {
			sb.append(String.format("%.4f", value / (BYTE_TB * 1.0)));
			sb.append(BYTE_UNITS_TB);
		}
		return sb.toString();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected final void onPostExecute(final Long[] result) {
		if (this.updateGUI) {
			this.updateText();

			ProgressBar pb = this.pbData;
			if (result[1] > 0) {
				pb.setMax((int) (result[1] / BYTE_KB));
				pb.setProgress((int) (result[0] / BYTE_KB));
				pb.setVisibility(View.VISIBLE);
				final String s = ((result[0] * CallMeter.HUNDRET) / result[1])
						+ "%";
				if (this.prefs.getBoolean(PREFS_DATA_INCOMING_ONLY, false)) {
					this.twDataIn.setText(s + " / " + this.twDataIn.getText());
					this.twPBDataText.setVisibility(View.GONE);
				} else {
					this.twPBDataText.setText(s + " / "
							+ prettyBytes(result[0]));
					this.twPBDataText.setVisibility(View.VISIBLE);
				}
			} else {
				pb.setVisibility(View.GONE);
				this.twPBDataText.setVisibility(View.GONE);
			}
		}

		// FIXME if (this.updateGUI) {
		// ((CallMeter) this.context)
		// .setProgressBarIndeterminateVisibility(false);
		// }
	}

	/**
	 * Return Billdate as Calendar for a given day of month.
	 * 
	 * @param p
	 *            {@link SharedPreferences}
	 * @return date as Calendar
	 */
	static final Calendar getBillDate(final SharedPreferences p) {
		Calendar cal = Calendar.getInstance();
		if (!p.getBoolean(PREFS_DATA_EACHDAY, false)) {
			int billDay = 0;
			try {
				billDay = Integer.parseInt(p.getString(Updater.PREFS_BILLDAY,
						"0"));
			} catch (NumberFormatException e) {
				billDay = 0;
			}
			if (cal.get(Calendar.DAY_OF_MONTH) < billDay) {
				cal.add(Calendar.MONTH, -1);
			}
			cal.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), billDay);
		}
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal;
	}

	/**
	 * Update traffic data.
	 * 
	 * @param context
	 *            Context
	 * @param prefs
	 *            preferences
	 */
	static final synchronized void updateTraffic(final Context context,
			final SharedPreferences prefs) {
		if (!prefs.getBoolean(PREFS_DATA_ENABLE, false)) {
			return;
		}
		checkBillperiod(prefs);

		long runningIn = prefs.getLong(PREFS_DATA_RUNNING_IN, 0);
		long runningOut = prefs.getLong(PREFS_DATA_RUNNING_OUT, 0);

		final Device d = Device.getDevice();
		final String inter = d.getCell();
		if (inter != null) {
			try {
				final long rx = SysClassNet.getRxBytes(inter);
				final long tx = SysClassNet.getTxBytes(inter);
				runningIn = rx;
				runningOut = tx;
			} catch (IOException e) {
				Log.e(TAG, "I/O Error", e);
			}
		}

		final SharedPreferences.Editor editor = prefs.edit();
		editor.putLong(PREFS_DATA_RUNNING_IN, runningIn);
		editor.putLong(PREFS_DATA_RUNNING_OUT, runningOut);
		editor.commit();
	}

	/**
	 * Check if billing period changed.
	 * 
	 * @param prefs
	 *            preferences
	 */
	static final void checkBillperiod(final SharedPreferences prefs) {
		final Calendar billDate = UpdaterData.getBillDate(prefs);
		long lastBill = billDate.getTimeInMillis();
		long now = System.currentTimeMillis();
		long lastCheck = prefs.getLong(PREFS_DATA_LASTCHECK, 0);

		final SharedPreferences.Editor editor = prefs.edit();
		if (lastCheck < lastBill) {
			long preBootIn = prefs.getLong(PREFS_DATA_BOOT_IN, 0);
			long preBootOut = prefs.getLong(PREFS_DATA_BOOT_OUT, 0);
			long runningIn = prefs.getLong(PREFS_DATA_RUNNING_IN, 0);
			long runningOut = prefs.getLong(PREFS_DATA_RUNNING_OUT, 0);
			editor.putLong(PREFS_DATA_PREBILLING_IN, preBootIn + runningIn);
			editor.putLong(PREFS_DATA_PREBILLING_OUT, preBootOut + runningOut);
		}
		editor.putLong(PREFS_DATA_LASTCHECK, now);
		editor.commit();
	}

	/**
	 * Move traffic from thisboot to preboot.
	 * 
	 * @param prefs
	 *            preferences
	 */
	static final void checkPostboot(final SharedPreferences prefs) {
		long preBootIn = prefs.getLong(PREFS_DATA_BOOT_IN, 0);
		long preBootOut = prefs.getLong(PREFS_DATA_BOOT_OUT, 0);
		long runningIn = prefs.getLong(PREFS_DATA_RUNNING_IN, 0);
		long runningOut = prefs.getLong(PREFS_DATA_RUNNING_OUT, 0);

		if (runningIn == 0 && runningOut == 0) {
			return;
		}

		preBootIn += runningIn;
		runningIn = 0;
		preBootOut += runningOut;
		runningOut = 0;

		final SharedPreferences.Editor editor = prefs.edit();
		editor.putLong(PREFS_DATA_BOOT_IN, preBootIn);
		editor.putLong(PREFS_DATA_BOOT_OUT, preBootOut);
		editor.putLong(PREFS_DATA_RUNNING_IN, runningIn);
		editor.putLong(PREFS_DATA_RUNNING_OUT, runningOut);
		editor.commit();
	}
}
