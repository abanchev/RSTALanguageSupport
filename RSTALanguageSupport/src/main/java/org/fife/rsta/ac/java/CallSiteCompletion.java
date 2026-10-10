/*
 * This library is distributed under a modified BSD license.  See the included
 * LICENSE.md file for details.
 */
package org.fife.rsta.ac.java;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;


/**
 * Where a completion lands in existing code, as VS Code's "replace" insert
 * mode and IntelliJ's Tab-to-replace see it: the rest of the identifier after
 * the caret is part of the word being replaced, and an argument list that is
 * already there is kept rather than a second one inserted.
 */
final class CallSiteCompletion {

	private static final int LOOKAHEAD = 4096;

	private CallSiteCompletion() {
	}


	/**
	 * Length of the identifier characters right after <code>offs</code>.
	 */
	static int identifierTail(Document doc, int offs) {
		String text = text(doc, offs, 256);
		int tail = 0;
		while (tail<text.length() &&
				Character.isJavaIdentifierPart(text.charAt(tail))) {
			tail++;
		}
		return tail;
	}


	/**
	 * Length of the identifier characters right before <code>offs</code>.
	 */
	static int identifierHead(Document doc, int offs) {
		int from = Math.max(0, offs - 256);
		String text = text(doc, from, offs - from);
		int head = 0;
		while (head<text.length() &&
				Character.isJavaIdentifierPart(text.charAt(text.length() - 1 - head))) {
			head++;
		}
		return head;
	}


	/**
	 * The bare method name of a completion's replacement text: no argument
	 * list and no qualifier.
	 */
	static String methodName(String replacement) {
		String name = replacement==null ? "" : replacement;
		int paren = name.indexOf('(');
		if (paren>=0) {
			name = name.substring(0, paren);
		}
		return name.substring(name.lastIndexOf('.') + 1).trim();
	}


	/**
	 * Offset of the <code>(</code> of an argument list starting at
	 * <code>offs</code> (spaces and tabs before it allowed), or -1.
	 */
	static int existingCall(Document doc, int offs) {
		String text = text(doc, offs, 64);
		int i = 0;
		while (i<text.length() &&
				(text.charAt(i)==' ' || text.charAt(i)=='\t')) {
			i++;
		}
		return i<text.length() && text.charAt(i)=='(' ? offs + i : -1;
	}


	/**
	 * <code>{start, end}</code> of argument <code>index</code> in the list
	 * opened at <code>openParen</code>, surrounding whitespace excluded, or
	 * <code>null</code> when the list has fewer arguments or is unclosed.
	 */
	static int[] argument(Document doc, int openParen, int index) {
		String text = text(doc, openParen + 1, LOOKAHEAD);
		int depth = 0;
		int arg = 0;
		int start = 0;
		char quote = 0;
		for (int i=0; i<text.length(); i++) {
			char c = text.charAt(i);
			if (quote!=0) {
				if (c=='\\') {
					i++;
				}
				else if (c==quote) {
					quote = 0;
				}
			}
			else if (c=='"' || c=='\'') {
				quote = c;
			}
			else if (c=='(' || c=='[' || c=='{') {
				depth++;
			}
			else if (c==')' || c==']' || c=='}') {
				if (depth==0) {
					return arg==index ? trim(text, openParen + 1, start, i) : null;
				}
				depth--;
			}
			else if (c==',' && depth==0) {
				if (arg==index) {
					return trim(text, openParen + 1, start, i);
				}
				arg++;
				start = i + 1;
			}
		}
		return null;
	}


	/**
	 * Which argument of the list opened at <code>openParen</code> contains
	 * <code>caret</code>, or -1 when the caret is outside the list.
	 */
	static int argumentAt(Document doc, int openParen, int caret) {
		if (caret<=openParen) {
			return -1;
		}
		String text = text(doc, openParen + 1, LOOKAHEAD);
		int depth = 0;
		int arg = 0;
		char quote = 0;
		for (int i=0; i<text.length(); i++) {
			if (openParen + 1 + i>=caret) {
				return arg;
			}
			char c = text.charAt(i);
			if (quote!=0) {
				if (c=='\\') {
					i++;
				}
				else if (c==quote) {
					quote = 0;
				}
			}
			else if (c=='"' || c=='\'') {
				quote = c;
			}
			else if (c=='(' || c=='[' || c=='{') {
				depth++;
			}
			else if (c==')' || c==']' || c=='}') {
				if (depth==0) {
					return -1;
				}
				depth--;
			}
			else if (c==',' && depth==0) {
				arg++;
			}
		}
		return -1;
	}


	/**
	 * Puts the caret in the first argument of the list opened at
	 * <code>openParen</code>, selecting it so typing replaces it.
	 */
	static void selectFirstArgument(JTextComponent tc, int openParen) {
		int[] arg = argument(tc.getDocument(), openParen, 0);
		if (arg==null || arg[1]<=arg[0]) {
			tc.setCaretPosition(openParen + 1);
		}
		else {
			tc.setCaretPosition(arg[0]);
			tc.moveCaretPosition(arg[1]);
		}
	}


	private static int[] trim(String text, int base, int start, int end) {
		while (start<end && Character.isWhitespace(text.charAt(start))) {
			start++;
		}
		while (end>start && Character.isWhitespace(text.charAt(end - 1))) {
			end--;
		}
		return new int[] { base + start, base + end };
	}


	private static String text(Document doc, int offs, int max) {
		int len = Math.max(0, Math.min(max, doc.getLength() - offs));
		try {
			return len==0 ? "" : doc.getText(offs, len);
		} catch (BadLocationException ble) {
			return "";
		}
	}


}
