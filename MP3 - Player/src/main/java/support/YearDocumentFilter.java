//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package support;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

final class YearDocumentFilter extends DocumentFilter {
    YearDocumentFilter() {
    }

    private boolean isValid(String testText) {
        if (testText.length() > 4) {
            return false;
        } else if (testText.isEmpty()) {
            return true;
        } else {
            int intValue;
            try {
                intValue = Integer.parseInt(testText.trim());
            } catch (NumberFormatException var4) {
                return false;
            }

            return intValue >= 0 && intValue <= 9999;
        }
    }

    public void insertString(DocumentFilter.FilterBypass fb, int offset, String text, AttributeSet attr) throws BadLocationException {
        StringBuilder sb = new StringBuilder();
        sb.append(fb.getDocument().getText(0, fb.getDocument().getLength()));
        sb.insert(offset, text);
        if (this.isValid(sb.toString())) {
            super.insertString(fb, offset, text, attr);
        }

    }

    public void replace(DocumentFilter.FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
        StringBuilder sb = new StringBuilder();
        sb.append(fb.getDocument().getText(0, fb.getDocument().getLength()));
        int end = offset + length;
        sb.replace(offset, end, text);
        if (this.isValid(sb.toString())) {
            super.replace(fb, offset, length, text, attrs);
        }

    }

    public void remove(DocumentFilter.FilterBypass fb, int offset, int length) throws BadLocationException {
        StringBuilder sb = new StringBuilder();
        sb.append(fb.getDocument().getText(0, fb.getDocument().getLength()));
        int end = offset + length;
        sb.delete(offset, end);
        if (this.isValid(sb.toString())) {
            super.remove(fb, offset, length);
        }

    }
}
