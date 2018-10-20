package eu.siacs.conversations.ui.util;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

public class PinEntryWrapper {

    private final List<EditText> digits = new ArrayList<>();

    private final TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        @Override
        public void afterTextChanged(Editable s) {
            int current = -1;
            for (int i = 0; i < digits.size(); ++i) {
                if (s.hashCode() == digits.get(i).getText().hashCode()) {
                    current = i;
                }
            }
            if (current == -1) {
                return;
            }
            if (s.length() > 0) {
                if (current < digits.size() - 1) {
                    digits.get(current + 1).requestFocus();
                }
            } else {
                int focusOn = current;
                for (int i = current - 1; i >= 0; --i) {
                    if (digits.get(i).getText().length() == 0) {
                        focusOn = i;
                    } else {
                        break;
                    }
                }
                digits.get(focusOn).requestFocus();
            }
        }
    };

    private final View.OnKeyListener keyListener = (v, keyCode, event) -> {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }
        if (v instanceof EditText) {
            final EditText editText = (EditText) v;
            if (keyCode == KeyEvent.KEYCODE_DEL && editText.getText().length() == 0) {
                final int current = digits.indexOf(editText);
                for (int i = current - 1; i >= 0; --i) {
                    if (digits.get(i).getText().length() > 0) {
                        digits.get(i).getText().clear();
                        return true;
                    }
                }
            }
        }
        return false;
    };

    public PinEntryWrapper(LinearLayout linearLayout) {
        for (int i = 0; i < linearLayout.getChildCount(); ++i) {
            View view = linearLayout.getChildAt(i);
            if (view instanceof EditText) {
                this.digits.add((EditText) view);
            }
        }
        registerListeners();
    }

    private void registerListeners() {
        for (EditText editText : digits) {
            editText.addTextChangedListener(textWatcher);
            editText.setOnKeyListener(keyListener);
        }
    }

}