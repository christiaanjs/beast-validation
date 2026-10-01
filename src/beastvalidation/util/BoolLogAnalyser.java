package beastvalidation.util;

import java.io.IOException;
import java.util.List;

import beastfx.app.tools.LogAnalyser;

/**
 * LogAnalyser that reads true/false columns as true = 1, false = 0.
 *
 * BEAST 2 logs booleans as 1/0, but BEAST 3 logs them as true/false. LogAnalyser codes
 * non-numeric values by order of first appearance, so a true/false column whose first
 * value is true (e.g. BSSVS rate indicators, which start all true) was read as
 * true = 0, false = 1, i.e. inverted. This class recodes such columns after reading.
 */
public class BoolLogAnalyser extends LogAnalyser {

	public BoolLogAnalyser(String fileName, int burnInPercentage, boolean quiet, boolean calcStats) throws IOException {
		super(fileName, burnInPercentage, quiet, calcStats);
	}

	@Override
	protected void readLogFile(String fileName, int burnInPercentage) throws IOException {
		super.readLogFile(fileName, burnInPercentage);
		for (int i = 0; i < m_ranges.length; i++) {
			List<String> values = m_ranges[i];
			if (values == null || values.isEmpty() || !isBoolean(values)) {
				continue;
			}
			// m_fTraces[i] holds indices into values; replace them by 1 (true) or 0 (false)
			Double[] trace = m_fTraces[i];
			for (int j = 0; j < trace.length; j++) {
				if (trace[j] != null) {
					trace[j] = values.get((int) (double) trace[j]).equals("true") ? 1.0 : 0.0;
				}
			}
			// numeric from now on, so calcStats() also gives median, HPD and ESS
			m_types[i] = type.INTEGER;
		}
	}

	private static boolean isBoolean(List<String> values) {
		for (String v : values) {
			if (!v.equals("true") && !v.equals("false")) {
				return false;
			}
		}
		return true;
	}
}
