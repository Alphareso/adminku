package com.bdajaya.adminku.ui.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.bdajaya.adminku.R;
import com.bdajaya.adminku.databinding.ViewCardSelectBinding;
import com.bdajaya.adminku.ui.components.RoundedBackground;

public class CardSelectView extends FrameLayout {

    private ViewCardSelectBinding b;
    private boolean showIcon = false;
    private boolean required = false;
    private CharSequence titleText = "";
    private int requiredColor = 0;

    public CardSelectView(Context c) { this(c, null); }
    public CardSelectView(Context c, @Nullable AttributeSet a) { this(c, a, 0); }
    public CardSelectView(Context c, @Nullable AttributeSet a, int s) {
        super(c, a, s);
        b = ViewCardSelectBinding.inflate(LayoutInflater.from(c), this);

        TypedArray ta = c.obtainStyledAttributes(a, R.styleable.CardSelectView, s, 0);
        String title = ta.getString(R.styleable.CardSelectView_csv_title);
        String value = ta.getString(R.styleable.CardSelectView_csv_value);
        required = ta.getBoolean(R.styleable.CardSelectView_csv_required, false);
        showIcon = ta.getBoolean(R.styleable.CardSelectView_csv_showIcon, false);
        int pos = ta.getInt(R.styleable.CardSelectView_csv_positionGroup, 3);
        int bg = ta.getColor(R.styleable.CardSelectView_csv_colorBackground,
                getResources().getColor(R.color.surface, getContext().getTheme()));
        int inputColor = ta.getColor(R.styleable.CardSelectView_csv_colorInput,
                getResources().getColor(R.color.primary_text, getContext().getTheme()));
        int iconColor = ta.getColor(R.styleable.CardSelectView_csv_colorIcon,
                getResources().getColor(R.color.primary_text, getContext().getTheme()));
        int titleColor = ta.getColor(R.styleable.CardSelectView_csv_colorTitle,
                getResources().getColor(R.color.secondary_text, getContext().getTheme()));
        requiredColor = ta.getColor(R.styleable.CardSelectView_csv_colorRequired,
                getResources().getColor(R.color.primary, getContext().getTheme()));
        int colorStroke = ta.getColor(R.styleable.CardSelectView_csv_colorStroke,
                getResources().getColor(R.color.outline, getContext().getTheme()));

        int iconRes = ta.getResourceId(R.styleable.CardSelectView_csv_iconSrc, R.drawable.ic_category);
        ta.recycle();

        b.root.setBackground(RoundedBackground.build(getContext(),
                16f, bg,
                getResources().getColor(R.color.outline, getContext().getTheme()),
                RoundedBackground.dp(getContext(), 1), pos));

        titleText = title != null ? title : "";
        b.csvName.setText(value != null ? value : "");
        b.csvValue.setText(value != null ? value : "");
        b.icon.setImageResource(iconRes);

        b.icon.setVisibility(showIcon ? VISIBLE : GONE);
        if (showIcon && iconRes != 0) b.icon.setImageResource(iconRes);

        // Set colors
        b.root.setBackground(RoundedBackground.build(getContext(),16f, bg, colorStroke,
                RoundedBackground.dp(getContext(),1), pos));
        b.icon.setColorFilter(iconColor);
        b.csvTitle.setTextColor(titleColor);
        applyRequiredIndicator();

        b.csvName.setTextColor(inputColor);
        b.csvValue.setTextColor(inputColor);
    }

    private void applyRequiredIndicator() {
        if (TextUtils.isEmpty(titleText)) {
            b.csvTitle.setText("");
            return;
        }
        if (!required) {
            b.csvTitle.setText(titleText);
            return;
        }
        String display = titleText + " *";
        SpannableString ss = new SpannableString(display);
        int starIndex = display.indexOf('*');
        if (starIndex >= 0) {
            int color = requiredColor != 0
                    ? requiredColor
                    : getResources().getColor(R.color.primary, getContext().getTheme());
            ss.setSpan(new ForegroundColorSpan(color), starIndex, starIndex + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        b.csvTitle.setText(ss);
    }

    private boolean hasSelection() {
        CharSequence primary = b.csvName.getText();
        return primary != null && primary.toString().trim().length() > 0;
    }

    public void setPrimaryText(String text) {
        b.csvName.setText(text != null ? text : "");
        if (text != null && text.trim().length() > 0) {
            clearError();
        }
    }

    public void setSecondaryText(String text) {
        b.csvValue.setText(text != null ? text : "");
        if (hasSelection()) {
            clearError();
        }
    }

    public void setValue(String v) {
        b.csvValue.setText(v);
        if (hasSelection()) {
            clearError();
        }
    }

    public String getValue() {
        return b.csvValue.getText().toString();
    }

    public void setError(@Nullable String message) {
        if (TextUtils.isEmpty(message)) {
            b.errorText.setVisibility(GONE);
        } else {
            b.errorText.setText(message);
            b.errorText.setVisibility(VISIBLE);
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

    public boolean validate(@Nullable String message) {
        if (!required) {
            clearError();
            return true;
        }
        if (hasSelection()) {
            clearError();
            return true;
        }
        if (TextUtils.isEmpty(message)) {
            message = getContext().getString(R.string.error_field_required);
        }
        setError(message);
        return false;
    }

    public void setOnRowClick(OnClickListener l) {
        b.row.setOnClickListener(l);
    }
    public void setTrailing(boolean visible) {
        b.trailing.setVisibility(visible ? VISIBLE : GONE);
    }

    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        b.row.setEnabled(enabled);
        b.row2.setEnabled(enabled);
        b.trailing.setAlpha(enabled ? 1f : 0.5f);
    }

}
