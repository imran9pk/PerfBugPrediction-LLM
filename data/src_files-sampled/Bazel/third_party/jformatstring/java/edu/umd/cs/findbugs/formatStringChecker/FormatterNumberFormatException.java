package edu.umd.cs.findbugs.formatStringChecker;

public class FormatterNumberFormatException extends FormatterException {
	private static final long serialVersionUID = 1L;
	final String txt, kind;

	public String getTxt() {
		return txt;
	}

	public String getKind() {
		return kind;
	}

	public FormatterNumberFormatException(String txt, String kind) {
		this.txt = txt;
		this.kind = kind;

	}
}
