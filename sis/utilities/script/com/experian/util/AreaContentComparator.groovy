package com.experian.util;

import java.util.Comparator;

public class AreaContentComparator  implements Comparator<String>{

	@Override
	public int compare(String o1, String o2) {

		return convert(o1).compareToIgnoreCase(convert(o2));
	}


	public String convert(String str) {

		StringBuilder sb = new StringBuilder(str);

		int bindex = sb.indexOf("[");
		int endindex = 0;

		while (bindex != -1) {
			endindex = sb.indexOf("]", bindex);

			String tmp = "";

			switch (endindex - bindex - 1) {
				case 5:
					tmp = "";
					break;
				case 4:
					tmp = "0";
					break;
				case 3:
					tmp = "00";
					break;
				case 2:
					tmp = "000";
					break;
				case 1:
					tmp = "0000";
					break;

			}

			sb.insert(bindex + 1, tmp);

			bindex = sb.indexOf("[", endindex);
		}

		return sb.toString();
	}


}
