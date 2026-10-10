/*
 * This library is distributed under a modified BSD license.  See the included
 * LICENSE.md file for details.
 */
package org.fife.rsta.ac.java;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.CaretListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Position;
import org.fife.ui.autocomplete.ParameterizedCompletion;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;


/**
 * VS Code's signature help for a call whose argument list already existed:
 * the method's signature with the parameter under the caret in bold, and its
 * documentation, shown under the line while the caret stays in the list.
 */
final class SignatureTip {

	private static SignatureTip showing;

	private final RSyntaxTextArea area;
	private final ParameterizedCompletion pc;
	private final Position openParen;
	private final JEditorPane view = new JEditorPane("text/html", "");
	private final CaretListener caretListener = e -> update();
	private final FocusListener focusListener = new FocusAdapter() {
		@Override
		public void focusLost(FocusEvent e) {
			hide();
		}
	};
	private Popup popup;
	private Object previousEscape;
	private int shownArg = -2;


	private SignatureTip(RSyntaxTextArea area, ParameterizedCompletion pc,
			Position openParen) {
		this.area = area;
		this.pc = pc;
		this.openParen = openParen;
		view.setEditable(false);
		view.setFocusable(false);
		view.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
		view.setFont(UIManager.getFont("ToolTip.font"));
		view.setBackground(UIManager.getColor("ToolTip.background"));
		view.setForeground(UIManager.getColor("ToolTip.foreground"));
	}


	/**
	 * Shows the tip for <code>pc</code>, whose argument list opens at
	 * <code>openParen</code>, replacing any tip already showing.
	 */
	static void show(RSyntaxTextArea area, ParameterizedCompletion pc, int openParen) {
		if (showing!=null) {
			showing.hide();
		}
		try {
			showing = new SignatureTip(area, pc,
					area.getDocument().createPosition(openParen));
		} catch (BadLocationException ble) {
			return;
		}
		showing.install();
		showing.update();
	}


	private void install() {
		area.addCaretListener(caretListener);
		area.addFocusListener(focusListener);
		previousEscape = area.getInputMap(JComponent.WHEN_FOCUSED).get(
				KeyStroke.getKeyStroke("ESCAPE"));
		area.getInputMap(JComponent.WHEN_FOCUSED).put(
				KeyStroke.getKeyStroke("ESCAPE"), "SignatureTip.hide");
		area.getActionMap().put("SignatureTip.hide", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				hide();
			}
		});
	}


	private void update() {
		int arg = CallSiteCompletion.argumentAt(area.getDocument(),
				openParen.getOffset(), area.getCaretPosition());
		if (arg<0) {
			hide();
			return;
		}
		if (arg==shownArg && popup!=null) {
			return;
		}
		shownArg = arg;
		view.setText(html(arg));
		Point at = location();
		if (at==null) {
			hide();
			return;
		}
		if (popup!=null) {
			popup.hide();
		}
		JScrollPane box = new JScrollPane(view);
		box.setBorder(UIManager.getBorder("ToolTip.border"));
		view.setSize(520, Short.MAX_VALUE);
		java.awt.Dimension size = view.getPreferredSize();
		box.setPreferredSize(new java.awt.Dimension(Math.min(540, size.width + 8),
				Math.min(220, size.height + 8)));
		popup = PopupFactory.getSharedInstance().getPopup(area, box, at.x, at.y);
		popup.show();
	}


	private Point location() {
		try {
			Rectangle r = area.modelToView2D(openParen.getOffset()).getBounds();
			Point p = new Point(r.x, r.y + r.height + 2);
			SwingUtilities.convertPointToScreen(p, area);
			return p;
		} catch (BadLocationException | NullPointerException e) {
			return null;
		}
	}


	private String html(int arg) {
		StringBuilder sb = new StringBuilder("<html><body style='padding:2px 4px'><code>");
		String definition = pc.getInputText();
		if (definition!=null && definition.indexOf('(')>=0) {
			definition = definition.substring(0, definition.indexOf('('));
		}
		sb.append(escape(definition)).append('(');
		for (int i=0; i<pc.getParamCount(); i++) {
			ParameterizedCompletion.Parameter p = pc.getParam(i);
			if (i>0) {
				sb.append(", ");
			}
			String text = (p.getType()!=null ? p.getType() + " " : "") +
					(p.getName()!=null ? p.getName() : "");
			if (i==arg) {
				sb.append("<b><u>").append(escape(text)).append("</u></b>");
			}
			else {
				sb.append(escape(text));
			}
		}
		sb.append(")</code>");
		if (arg<pc.getParamCount()) {
			String desc = pc.getParam(arg).getDescription();
			if (desc!=null && !desc.isEmpty()) {
				sb.append("<br><i>").append(escape(pc.getParam(arg).getName()))
					.append("</i> – ").append(desc);
			}
		}
		String summary = pc.getSummary();
		if (summary!=null && !summary.isEmpty()) {
			sb.append("<hr>").append(stripHtml(summary));
		}
		return sb.append("</body></html>").toString();
	}


	private static String stripHtml(String html) {
		return html.replaceAll("(?is)</?(html|body)[^>]*>", "");
	}


	private static String escape(String s) {
		return s==null ? "" : s.replace("&", "&amp;").replace("<", "&lt;")
				.replace(">", "&gt;");
	}


	private void hide() {
		if (popup!=null) {
			popup.hide();
			popup = null;
		}
		area.removeCaretListener(caretListener);
		area.removeFocusListener(focusListener);
		area.getActionMap().remove("SignatureTip.hide");
		if (previousEscape!=null) {
			area.getInputMap(JComponent.WHEN_FOCUSED).put(
					KeyStroke.getKeyStroke("ESCAPE"), previousEscape);
		}
		else {
			area.getInputMap(JComponent.WHEN_FOCUSED).remove(
					KeyStroke.getKeyStroke("ESCAPE"));
		}
		if (showing==this) {
			showing = null;
		}
	}


}
