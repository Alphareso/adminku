package com.bdajaya.adminku.ui.components;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.bdajaya.adminku.R;
import com.bdajaya.adminku.data.manager.ImageStorageManager;
import com.bdajaya.adminku.databinding.ViewCardPhotoSelectorBinding;
import com.bdajaya.adminku.util.GlideEngine;
import com.bdajaya.adminku.util.UCropEngine;
import com.luck.picture.lib.basic.PictureSelector;
import com.luck.picture.lib.config.SelectMimeType;
import com.luck.picture.lib.config.SelectModeConfig;
import com.luck.picture.lib.entity.LocalMedia;
import com.luck.picture.lib.interfaces.OnResultCallbackListener;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CardPhotoSelector extends FrameLayout {

    // Changed: Return Uri list instead of Base64
    public interface OnImagesChanged {
        void onChanged(List<Uri> imageUris);
    }

    public interface OnPhotoClickListener {
        void onPhotoClick(int position, Uri uri);
    }

    private ViewCardPhotoSelectorBinding cpsBinding;
    private final PhotoThumbAdapter adapter = new PhotoThumbAdapter();
    private int maxCount = 8;
    private boolean required = false;
    private CharSequence titleText = "";
    private int requiredColor = 0;
    private OnImagesChanged callback;
    private OnPhotoClickListener photoClickListener;
    private final ImageStorageManager imageStorageManager;

    // Store Uris instead of base64
    private final List<Uri> selectedUris = new ArrayList<>();

    public CardPhotoSelector(Context c) { this(c, null); }
    public CardPhotoSelector(Context c, @Nullable AttributeSet a) { this(c, a, 0); }
    public CardPhotoSelector(Context c, @Nullable AttributeSet a, int s) {
        super(c, a, s);
        cpsBinding = ViewCardPhotoSelectorBinding.inflate(LayoutInflater.from(c), this);
        imageStorageManager = new ImageStorageManager(c);

        TypedArray ta = c.obtainStyledAttributes(a, R.styleable.CardPhotoSelector, s, 0);
        String title = ta.getString(R.styleable.CardPhotoSelector_cps_title);
        required = ta.getBoolean(R.styleable.CardPhotoSelector_cps_required, false);
        maxCount = ta.getInt(R.styleable.CardPhotoSelector_cps_maxCount, 8);
        int pos = ta.getInt(R.styleable.CardPhotoSelector_cps_positionGroup, 3);
        int cBg = ta.getColor(R.styleable.CardPhotoSelector_cps_colorBackground,
                getResources().getColor(R.color.surface, getContext().getTheme()));
        int colorTitle = ta.getColor(R.styleable.CardPhotoSelector_cps_colorTitle,
                getResources().getColor(R.color.primary_text, getContext().getTheme()));
        int cStroke = ta.getColor(R.styleable.CardPhotoSelector_cps_colorStroke,
                getResources().getColor(R.color.outline, getContext().getTheme()));
        int cRequired = ta.getColor(R.styleable.CardPhotoSelector_cps_colorRequired,
                getResources().getColor(R.color.primary, getContext().getTheme()));
        ta.recycle();

        cpsBinding.root.setBackground(RoundedBackground.build(getContext(),
                16f, cBg, cStroke,
                RoundedBackground.dp(getContext(), 1), pos));

        // Build title text
        String defaultTitle = getResources().getString(R.string.product_photos);
        titleText = title != null ? title : defaultTitle;
        requiredColor = cRequired;
        applyRequiredIndicator();
        cpsBinding.title.setTextColor(colorTitle);
        cpsBinding.recycler.setLayoutManager(new GridLayoutManager(getContext(), 4));
        cpsBinding.recycler.setAdapter(adapter);

        // Setup drag and drop
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(adapter.getItemTouchHelperCallback());
        itemTouchHelper.attachToRecyclerView(cpsBinding.recycler);
        adapter.setItemTouchHelper(itemTouchHelper);

        adapter.setOnAddClick(() -> launchPicker());
        adapter.setOnRemoveClick((i) -> {
            selectedUris.remove(i);
            adapter.removeImage(i);
            fire();
        });

        adapter.setOnOrderChanged(this::applyReorderedSelection);

        adapter.setOnImageClick((position, uri) -> {
            if (photoClickListener != null) {
                photoClickListener.onPhotoClick(position, uri);
            }
        });
    }

    private void applyRequiredIndicator() {
        if (TextUtils.isEmpty(titleText)) {
            cpsBinding.title.setText("");
            return;
        }
        if (!required) {
            cpsBinding.title.setText(titleText);
            return;
        }
        String display = titleText + " *";
        SpannableString spannable = new SpannableString(display);
        int starIndex = display.indexOf('*');
        if (starIndex >= 0) {
            int color = requiredColor != 0
                    ? requiredColor
                    : getResources().getColor(R.color.primary, getContext().getTheme());
            spannable.setSpan(new ForegroundColorSpan(color), starIndex, starIndex + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        cpsBinding.title.setText(spannable);
    }

    private boolean hasSelection() {
        return !selectedUris.isEmpty();
    }

    private void launchPicker() {
        if (!(getContext() instanceof Activity)) return;
        Activity act = (Activity) getContext();

        PictureSelector.create(act)
                .openGallery(SelectMimeType.ofImage())
                .setSelectionMode(SelectModeConfig.MULTIPLE)
                .setMaxSelectNum(maxCount - adapter.getItemCountWithoutAdd())
                .setImageEngine(GlideEngine.createGlideEngine())
                .setCropEngine(new UCropEngine())
                .forResult(new OnResultCallbackListener<LocalMedia>() {
                    @Override
                    public void onResult(ArrayList<LocalMedia> result) {
                        List<Uri> addedUris = new ArrayList<>();

                        for (LocalMedia media : result) {
                            Uri uri = resolveMediaUri(media);
                            if (uri != null && !selectedUris.contains(uri)) {
                                addedUris.add(uri);
                                selectedUris.add(uri);
                                android.util.Log.d("CardPhotoSelector", "Added image URI: " + uri);
                            }
                        }

                        // Convert Uri to display in adapter
                        if (!addedUris.isEmpty()) {
                            adapter.addUris(addedUris);
                            fire();
                        }
                    }

                    @Override
                    public void onCancel() {}
                });
    }

    private void fire() {
        if (required && hasSelection()) {
            clearError();
        }
        if (callback != null) callback.onChanged(new ArrayList<>(selectedUris));
    }

    public void setOnImagesChanged(OnImagesChanged cb) {
        this.callback = cb;
    }

    public void setOnPhotoClickListener(OnPhotoClickListener listener) {
        this.photoClickListener = listener;
    }

    public List<Uri> getImageUris() {
        return new ArrayList<>(selectedUris);
    }

    public void setImageUris(List<Uri> uris) {
        setSelectionSilently(uris);
    }

    // For edit mode: load existing images from paths
    public void setImagePaths(Context context, List<String> paths) {
        List<Uri> uris = new ArrayList<>();
        if (paths != null) {
            for (String path : paths) {
                Uri uri = imageStorageManager.getImageUri(path);
                if (uri != null) {
                    uris.add(uri);
                }
            }
        }
        setSelectionSilently(uris);
    }

    public void replaceImageAt(int index, Uri uri) {
        if (uri == null) {
            return;
        }
        if (index < 0 || index >= selectedUris.size()) {
            return;
        }
        selectedUris.set(index, uri);
        adapter.updateUri(index, uri);
        fire();
    }

    public void setError(@Nullable String message) {
        if (TextUtils.isEmpty(message)) {
            cpsBinding.errorText.setVisibility(GONE);
        } else {
            cpsBinding.errorText.setText(message);
            cpsBinding.errorText.setVisibility(VISIBLE);
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
        if (hasSelection()) {
            clearError();
        }
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
            message = getContext().getString(R.string.error_photos_required);
        }
        setError(message);
        return false;
    }

    private Uri resolveMediaUri(LocalMedia media) {
        String path = media.getCutPath();
        if (TextUtils.isEmpty(path)) path = media.getCompressPath();
        if (TextUtils.isEmpty(path)) path = media.getRealPath();
        if (TextUtils.isEmpty(path)) path = media.getSandboxPath();
        if (TextUtils.isEmpty(path)) path = media.getPath();
        if (TextUtils.isEmpty(path)) {
            return null;
        }

        if (path.startsWith("content://") || path.startsWith("file://")) {
            return Uri.parse(path);
        }
        return Uri.fromFile(new File(path));
    }

    private void setSelectionSilently(List<Uri> uris) {
        List<Uri> safeUris = uris == null ? new ArrayList<>() : new ArrayList<>(uris);
        selectedUris.clear();
        selectedUris.addAll(safeUris);
        adapter.replaceUris(selectedUris);
        if (hasSelection()) {
            clearError();
        }
    }

    private void applyReorderedSelection(List<Uri> newOrder) {
        if (newOrder == null) {
            return;
        }
        selectedUris.clear();
        selectedUris.addAll(new ArrayList<>(newOrder));
        if (hasSelection()) {
            clearError();
        }
        fire();
    }
}
