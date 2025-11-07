package com.bdajaya.adminku.ui.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.text.*;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.*;
import android.widget.*;

import androidx.annotation.Nullable;

import com.bdajaya.adminku.R;
import com.bdajaya.adminku.databinding.ViewCardInputBinding;
import com.bdajaya.adminku.ui.components.RoundedBackground;
import com.bdajaya.adminku.util.CurrencyFormatter;
import com.bdajaya.adminku.util.SafeTextWatcher;

import java.util.Locale;

/** Reusable card input with 2 modes (column + compact row) and rich input kinds. */
public class CardInputView extends FrameLayout {

    public enum InputKind { TEXT, NUMBER, DECIMAL, CURRENCY, BARCODE, STOCK }
    public enum TextCase { UPPERCASE, PROPER, SENTENCE }

    /** Notify typed value; for CURRENCY/NUMBER/STOCK -> Long, DECIMAL -> Double, TEXT/BARCODE -> String */
    public interface OnValueChangedListener { void onChanged(Object value); }

    private TextWatcher currencyWatcher;
    private ViewCardInputBinding civBinding;
    private InputKind inputKind = InputKind.TEXT;
    private TextCase textCase = TextCase.SENTENCE;
    private boolean compactHorizontal = false;
    private boolean counterEnabled = false;
    private boolean multiline = false;
    private boolean required = false;
    private int maxLength = 0;
    private boolean showIcon = false;
    private CharSequence titleText = "";
    private int requiredColor = 0;

    private boolean isUpdating = false;
    private OnValueChangedListener listener;

    public CardInputView(Context c) { this(c, null); }
    public CardInputView(Context c, @Nullable AttributeSet a) { this(c, a, 0); }
    public CardInputView(Context c, @Nullable AttributeSet a, int s) {
        super(c, a, s);
        civBinding = ViewCardInputBinding.inflate(LayoutInflater.from(c), this);

        TypedArray ta = c.obtainStyledAttributes(a, R.styleable.CardInputView, s, 0);
        String title = ta.getString(R.styleable.CardInputView_civ_title);
        String hint = ta.getString(R.styleable.CardInputView_civ_hintText);
        String value = ta.getString(R.styleable.CardInputView_civ_valueText);
        inputKind = InputKind.values()[ta.getInt(R.styleable.CardInputView_civ_inputType, 0)];
        textCase = TextCase.values()[ta.getInt(R.styleable.CardInputView_civ_textCase, 2)];
        required = ta.getBoolean(R.styleable.CardInputView_civ_required, false);
        multiline = ta.getBoolean(R.styleable.CardInputView_civ_multiline, false);
        counterEnabled = ta.getBoolean(R.styleable.CardInputView_civ_counterEnabled, false);
        maxLength = ta.getInt(R.styleable.CardInputView_civ_maxLength, 0);
        showIcon = ta.getBoolean(R.styleable.CardInputView_civ_showIcon, false);
        compactHorizontal = ta.getBoolean(R.styleable.CardInputView_civ_compactHorizontal, false);

        int pos = ta.getInt(R.styleable.CardInputView_civ_positionGroup, 3);
        int titleAlign = ta.getInt(R.styleable.CardInputView_civ_titleAlignment, 0);

        int cBg = ta.getColor(R.styleable.CardInputView_civ_colorBackground,
                getResources().getColor(R.color.surface, getContext().getTheme()));
        int cTitle = ta.getColor(R.styleable.CardInputView_civ_colorTitle,
                getResources().getColor(R.color.primary_text, getContext().getTheme()));
        int cIcon = ta.getColor(R.styleable.CardInputView_civ_colorIcon,
                getResources().getColor(R.color.secondary_text, getContext().getTheme()));
        int cInput = ta.getColor(R.styleable.CardInputView_civ_colorInput,
                getResources().getColor(R.color.primary_text, getContext().getTheme()));
        int cStroke = ta.getColor(R.styleable.CardInputView_civ_colorStroke,
                getResources().getColor(R.color.outline, getContext().getTheme()));
        int cRequire = ta.getColor(R.styleable.CardInputView_civ_colorRequire,
                getResources().getColor(R.color.primary, getContext().getTheme()));
        int iconRes = ta.getResourceId(R.styleable.CardInputView_civ_iconSrc, 0);
        ta.recycle();

        civBinding.root.setBackground(RoundedBackground.build(getContext(),16f,cBg,
                cStroke,
                RoundedBackground.dp(getContext(),1), pos));

        // Title & colors
        titleText = title != null ? title : "";
        requiredColor = cRequire;
        civBinding.txtTitle.setTextColor(cTitle);
        applyRequiredIndicator();
        civBinding.editValue.setHint(hint);
        civBinding.editValue.setTextColor(cInput);
        civBinding.icLeft.setColorFilter(cIcon);
        civBinding.icLeft.setVisibility(showIcon ? VISIBLE : GONE);
        if (showIcon && iconRes != 0) civBinding.icLeft.setImageResource(iconRes);

        // Alignment
        if (titleAlign == 1) civBinding.txtTitle.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        else if (titleAlign == 2) civBinding.txtTitle.setTextAlignment(TEXT_ALIGNMENT_VIEW_END);

        applyLayoutMode(compactHorizontal);
        configInputType();

        // Ensure EditText is truly focusable & not intercepted by parent
        civBinding.container.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        civBinding.container.setFocusable(false);
        civBinding.container.setClickable(false);
        civBinding.editValue.setFocusable(true);
        civBinding.editValue.setFocusableInTouchMode(true);
        civBinding.editValue.setLongClickable(true);

        // Avoid ScrollView intercept when user drags inside EditText (esp. multiline/long text)
        civBinding.editValue.setOnTouchListener((v, ev) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });

        if (multiline) {
            civBinding.editValue.setSingleLine(false);
            civBinding.editValue.setMinLines(3);
            civBinding.editValue.setMaxLines(6);
            civBinding.editValue.setVerticalScrollBarEnabled(true);
            civBinding.editValue.setMovementMethod(new ScrollingMovementMethod());
        }

        if (maxLength > 0) {
            civBinding.counter.setVisibility(counterEnabled ? VISIBLE : GONE);
            civBinding.counter.setText(String.format("0/%d", maxLength));
            civBinding.editValue.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
            civBinding.editValue.addTextChangedListener(new SimpleTextWatcher() {
                @Override public void onTextChanged(CharSequence s, int st, int b1, int c) {
                    if (counterEnabled) civBinding.counter.setText(s.length()+"/"+maxLength);
                }
            });
        }

        // Case transform + value change callback (single source of truth)
        civBinding.editValue.addTextChangedListener(new SafeTextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                if (isUpdating) return;
                if (inputKind == InputKind.TEXT || inputKind == InputKind.BARCODE) {
                    final String t = s.toString();
                    final String out = applyCase(t);
                    if (!t.equals(out)) {
                        isUpdating = true;
                        civBinding.editValue.setText(out);
                        civBinding.editValue.setSelection(out.length());
                        isUpdating = false;
                    }
                }
                if (hasUserInput()) {
                    clearError();
                }
                if (listener != null) listener.onChanged(extractTypedValue());
            }
        });

        if (value != null) setValue(value);
    }

    /* ====== Internal helpers ====== */

    // Select all text in the input field
    public void selectAll() {
        civBinding.editValue.requestFocus();
        civBinding.editValue.selectAll();
    }

    private void applyLayoutMode(boolean compact) {
        LinearLayout container = civBinding.container;
        LinearLayout contentRow = civBinding.contentRow;
        LinearLayout inputColumn = civBinding.inputColumn;

        if (compact) {
            contentRow.setOrientation(LinearLayout.HORIZONTAL);

            LinearLayout.LayoutParams lpHeader = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f);
            civBinding.headerRow.setLayoutParams(lpHeader);

            LinearLayout.LayoutParams lpInputColumn = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lpInputColumn.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            inputColumn.setLayoutParams(lpInputColumn);

            civBinding.editValue.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            civBinding.editValue.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

            LinearLayout.LayoutParams lpCounter = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpCounter.gravity = Gravity.END;
            civBinding.counter.setLayoutParams(lpCounter);

            if (!multiline) {
                civBinding.editValue.setSingleLine(true);
            }
        } else {
            contentRow.setOrientation(LinearLayout.VERTICAL);

            civBinding.headerRow.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            inputColumn.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            civBinding.editValue.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            civBinding.txtTitle.setGravity(Gravity.START);
            civBinding.editValue.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);

            LinearLayout.LayoutParams lpCounter = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpCounter.gravity = Gravity.END;
            civBinding.counter.setLayoutParams(lpCounter);

            if (!multiline) {
                civBinding.editValue.setSingleLine(true);
                civBinding.editValue.setHorizontallyScrolling(false);
            }
        }

        ensureFocusableForInput();
        container.requestLayout();
        container.invalidate();

    }

    private void applyRequiredIndicator() {
        CharSequence base = titleText != null ? titleText : "";
        if (TextUtils.isEmpty(base)) {
            civBinding.txtTitle.setText("");
            return;
        }
        if (!required) {
            civBinding.txtTitle.setText(base);
            return;
        }
        String display = base + " *";
        SpannableString spannable = new SpannableString(display);
        int starIndex = display.indexOf('*');
        if (starIndex >= 0) {
            int color = requiredColor != 0
                    ? requiredColor
                    : getResources().getColor(R.color.primary, getContext().getTheme());
            spannable.setSpan(new ForegroundColorSpan(color), starIndex, starIndex + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        civBinding.txtTitle.setText(spannable);
    }

    private void ensureFocusableForInput() {
        // Container jangan ambil fokus/klik — biarkan turun ke anak (EditText)
        civBinding.container.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        civBinding.container.setFocusable(false);
        civBinding.container.setFocusableInTouchMode(false);
        civBinding.container.setClickable(false);

        // Anak non-input jangan fokus/clik
        civBinding.txtTitle.setFocusable(false);
        civBinding.txtTitle.setFocusableInTouchMode(false);
        civBinding.txtTitle.setClickable(false);
        if (civBinding.icLeft != null) {
            civBinding.icLeft.setFocusable(false);
            civBinding.icLeft.setClickable(false);
            civBinding.icLeft.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        // EditText harus fokusable
        civBinding.editValue.setFocusable(true);
        civBinding.editValue.setFocusableInTouchMode(true);
        civBinding.editValue.setLongClickable(true);

        // Hindari parent (ScrollView/RecyclerView) mencuri gesture
        civBinding.editValue.setOnTouchListener((v, ev) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false; // tetap biarkan EditText memproses event
        });
    }

    private void configInputType() {
        // bersihkan dulu filter & watcher khusus
        civBinding.editValue.setFilters(new InputFilter[]{});
        if (currencyWatcher != null) {
            civBinding.editValue.removeTextChangedListener(currencyWatcher);
            currencyWatcher = null;
        }

        switch (inputKind) {
            case TEXT:
                civBinding.editValue.setInputType(InputType.TYPE_CLASS_TEXT |
                        (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
                break;
            case NUMBER:
            case BARCODE:
            case STOCK:
                civBinding.editValue.setInputType(InputType.TYPE_CLASS_NUMBER);
                break;
            case DECIMAL:
                civBinding.editValue.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                break;
            case CURRENCY:
                civBinding.editValue.setInputType(InputType.TYPE_CLASS_NUMBER);
                currencyWatcher = new SafeTextWatcher() {
                    @Override public void afterTextChanged(Editable s) {
                        if (isUpdating) return;
                        String raw = s.toString();
                        String digits = raw.replaceAll("[^0-9]", "");
                        if (digits.isEmpty()) {
                            if (raw.isEmpty()) {
                                return;
                            }
                            isUpdating = true;
                            civBinding.editValue.setText("");
                            civBinding.editValue.setSelection(0);
                            isUpdating = false;
                            return;
                        }
                        long val = CurrencyFormatter.parseCurrencyToLong(raw);
                        String pretty = CurrencyFormatter.formatCurrency(val);
                        if (!pretty.equals(raw)) {
                            isUpdating = true;
                            civBinding.editValue.setText(pretty);
                            civBinding.editValue.setSelection(pretty.length());
                            isUpdating = false;
                        }
                        if (hasUserInput()) {
                            clearError();
                        }
                    }
                };
                civBinding.editValue.addTextChangedListener(currencyWatcher);
                break;
        }
    }

    private String applyCase(String in) {
        if (in.isEmpty()) return in;
        Locale locale = Locale.getDefault(); // atau new Locale("id", "ID")
        switch (textCase) {
            case UPPERCASE:
                return in.toUpperCase(locale);

            case PROPER: {
                StringBuilder sb = new StringBuilder(in.length());
                boolean capitalizeNext = true;
                for (int i = 0; i < in.length(); i++) {
                    char c = in.charAt(i);
                    if (Character.isWhitespace(c)) {
                        sb.append(c);
                        capitalizeNext = true; // huruf berikutnya jadi kapital
                    } else if (capitalizeNext) {
                        sb.append(Character.toUpperCase(c));
                        capitalizeNext = false;
                    } else {
                        sb.append(Character.toLowerCase(c));
                    }
                }
                return sb.toString();
            }

            case SENTENCE:
            default: {
                // Pertahankan spasi awal/akhir. Kapitalisasi huruf pertama non-whitespace.
                int i = 0;
                while (i < in.length() && Character.isWhitespace(in.charAt(i))) i++;
                if (i >= in.length()) return in; // semua spasi
                char first = Character.toUpperCase(in.charAt(i));
                return in.substring(0, i) + first + in.substring(i + 1);
            }
        }
    }


    private Object extractTypedValue() {
        String raw = getText();
        switch (inputKind) {
            case CURRENCY:
                return CurrencyFormatter.parseCurrencyToLong(raw);   // Long IDR
            case NUMBER:
            case STOCK:
            case BARCODE:
                try { return Long.parseLong(raw.replaceAll("[^0-9]","")); }
                catch (Exception e) { return 0L; }
            case DECIMAL:
                try { return Double.parseDouble(raw.replace(',', '.')); }
                catch (Exception e) { return 0d; }
            case TEXT:
            default:
                return raw;
        }
    }

    /* ====== Public API expected by callers ====== */

    public void setError (@Nullable String errorMsg) {
        civBinding.editValue.setError(errorMsg);
        if (TextUtils.isEmpty(errorMsg)) {
            civBinding.errorText.setVisibility(GONE);
        } else {
            civBinding.errorText.setText(errorMsg);
            civBinding.errorText.setVisibility(VISIBLE);
        }
    }

    public void clearError() {
        setError(null);
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
        applyRequiredIndicator();
    }

    public boolean validate() {
        return validate(null);
    }

    public boolean validate(@Nullable String errorMessage) {
        if (!required) {
            clearError();
            return true;
        }
        if (hasUserInput()) {
            clearError();
            return true;
        }
        if (TextUtils.isEmpty(errorMessage)) {
            errorMessage = getResources().getString(R.string.error_field_required);
        }
        setError(errorMessage);
        return false;
    }

    /** Generic string setter (used by margin/stock text). */
    public void setValue(String value) { setText(value); }

    /** Generic string getter (raw text without formatting changes). */
    public String getValue() { return getText(); }

    /** Set formatted currency (IDR long) and update view. */
    public void setCurrencyValue(long rupiah) {
        isUpdating = true;
        String pretty = CurrencyFormatter.formatCurrency(rupiah);
        civBinding.editValue.setText(pretty);
        civBinding.editValue.setSelection(pretty.length());
        isUpdating = false;
        if (listener != null) listener.onChanged(rupiah);
    }

    /** Get currency value as long IDR. */
    public long getCurrencyValue() {
        return CurrencyFormatter.parseCurrencyToLong(getText());
    }

    /** For external listeners (quick edit dialog, etc). */
    public void setOnValueChangedListener(OnValueChangedListener l) { this.listener = l; }

    /** Accessors used elsewhere */
    public EditText getEditText() { return civBinding.editValue; }
    public ImageView getIconView() { return civBinding.icLeft; }
    public TextView getTitleView() { return civBinding.txtTitle; }

    /** Generic text helpers used by setValue/getValue */
    public void setText(String value) {
        isUpdating = true;
        civBinding.editValue.setText(value == null ? "" : value);
        civBinding.editValue.setSelection(civBinding.editValue.length());
        isUpdating = false;
        if (listener != null) listener.onChanged(extractTypedValue());
        if (hasUserInput()) {
            clearError();
        }
    }
    public String getText() { return civBinding.editValue.getText().toString(); }

    /* ====== Convenience setters for mode/kind (optional) ====== */
    public void setInputKind(InputKind kind) { this.inputKind = kind; configInputType(); }
    public void setCompactHorizontal(boolean compact) { this.compactHorizontal = compact; applyLayoutMode(compact); }

    /** Simplified watcher */
    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) {}
    }

    private boolean hasUserInput() {
        String raw = getText();
        if (raw == null) return false;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return false;
        switch (inputKind) {
            case CURRENCY:
            case NUMBER:
            case STOCK:
                return trimmed.replaceAll("[^0-9]", "").length() > 0;
            case DECIMAL:
                try {
                    Double.parseDouble(trimmed.replace(',', '.'));
                    return true;
                } catch (Exception e) {
                    return false;
                }
            case BARCODE:
            case TEXT:
            default:
                return trimmed.length() > 0;
        }
    }
}
