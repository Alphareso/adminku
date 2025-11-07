package com.bdajaya.adminku.ui.activities;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import com.bdajaya.adminku.AdminkuApplication;
import com.bdajaya.adminku.R;
import com.bdajaya.adminku.data.AppDatabase;
import com.bdajaya.adminku.data.manager.ImageStorageManager;
import com.bdajaya.adminku.databinding.ActivityAddEditProductBinding;
import com.bdajaya.adminku.databinding.DialogPhotoPreviewBinding;
import com.bdajaya.adminku.ui.viewmodel.AddEditProductViewModel;
import com.bdajaya.adminku.util.CurrencyFormatter;
import com.bumptech.glide.Glide;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class AddEditProductActivity extends AppCompatActivity {
    private static final String STATUS_LIVE = "LIVE";
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final long DEFAULT_PRICE = 0L;
    private static final long DEFAULT_STOCK = 0L;

    private ActivityAddEditProductBinding binding;
    private AddEditProductViewModel viewModel;
    private ImageStorageManager imageStorage;

    private boolean hasUnsavedChanges = false;
    private boolean isPopulatingUi = false;
    private int pendingCropIndex = RecyclerView.NO_POSITION;
    private String selectedUnitId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);

        binding = ActivityAddEditProductBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.add_product);
        }
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int bottomPadding = Math.max(systemBars.bottom, imeBottom);
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottomPadding);
            return insets;
        });

        imageStorage = new ImageStorageManager(this);

        setupViewModel();
        loadExistingProductIfEditing();
        setupCategorySelection();
        setupCategoryDisplay();
        setupBrandSelection();
        setupBrandDisplay();
        setupUnitSelection();
        setupUnsavedChangeWatchers();
        setupPhotoSelector();
        setupSaveButtons();
        setupBackPressed();
    }

    private void setupViewModel() {
        try {
            viewModel = new ViewModelProvider(this).get(AddEditProductViewModel.class);

            observeCategorySelection();
            observeBrandSelection();
            observeProductData();

        } catch (Exception e) {
            Log.e("AddEditProductActivity", "Error setting up ViewModel", e);
            finish();
        }
    }

    private void loadExistingProductIfEditing() {
        String productId = getIntent().getStringExtra("productId");
        if (productId != null && !productId.trim().isEmpty()) {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(R.string.edit_product);
            }

            viewModel.loadProduct(productId);
            Log.d("AddEditProductActivity", "Loading product for editing: " + productId);

            // Load existing images
            loadExistingImages(productId);
        } else {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(R.string.add_product);
            }
            Log.d("AddEditProductActivity", "Adding new product");
        }
    }

    private void loadExistingImages(String productId) {

        AppDatabase.databaseWriteExecutor.execute(() -> {
            AdminkuApplication app = (AdminkuApplication) getApplication();
            List<com.bdajaya.adminku.data.entity.ProductImage> images =
                    app.getProductRepository().getProductImagesSync(productId);

            if (images != null && !images.isEmpty()) {
                // Sort images by orderIndex ascending to ensure reordered images show in correct display order (dragged first position shows first in product list)
                images.sort((img1, img2) -> Integer.compare(img1.getOrderIndex(), img2.getOrderIndex()));

                List<String> imagePaths = new java.util.ArrayList<>();
                for (com.bdajaya.adminku.data.entity.ProductImage img : images) {
                    imagePaths.add(img.getImagePath());
                }

                runOnUiThread(() -> {
                    // Load images ke CardPhotoSelector
                    binding.photoSelector.setImagePaths(this, imagePaths);
                });
            }
        });
    }

    private void observeProductData() {
        viewModel.getProductName().observe(this, name -> {
            if (name != null && !name.trim().isEmpty() && viewModel.isEditMode()) {
                updateUiSafely(() -> binding.nameInput.setText(name));
            }
        });

        viewModel.getProductDescription().observe(this, desc -> {
            if (desc != null && !desc.trim().isEmpty() && viewModel.isEditMode()) {
                updateUiSafely(() -> binding.descInput.setText(desc));
            }
        });

        viewModel.getProductBarcode().observe(this, barcode -> {
            if (barcode != null && !barcode.trim().isEmpty() && viewModel.isEditMode()) {
                updateUiSafely(() -> binding.barcodeInput.setText(barcode));
            }
        });

        viewModel.getProductBuyPrice().observe(this, buyPrice -> {
            if (buyPrice != null && buyPrice > 0 && viewModel.isEditMode()) {
                String formattedPrice = CurrencyFormatter.formatCurrency(buyPrice);
                updateUiSafely(() -> binding.buyPriceInput.setText(formattedPrice));
            }
        });

        viewModel.getProductSellPrice().observe(this, sellPrice -> {
            if (sellPrice != null && sellPrice > 0 && viewModel.isEditMode()) {
                String formattedPrice = CurrencyFormatter.formatCurrency(sellPrice);
                updateUiSafely(() -> binding.sellPriceInput.setText(formattedPrice));
            }
        });

        viewModel.getProductStock().observe(this, stock -> {
            if (stock != null && stock >= 0 && viewModel.isEditMode()) {
                updateUiSafely(() -> {
                    binding.buyStockInput.setText(String.valueOf(stock));
                    if (binding.sellStockInput != null) {
                        binding.sellStockInput.setText(String.valueOf(stock));
                    }
                });
            }
        });

        // AWAS! Kalau nanti nama kategori berubah, dan namanya tidak kosong, dan sedang mode edit
        // maka tampilkan nama baru itu di layar dengan cara yang aman.
        viewModel.getCategoryName().observe(this, categoryName -> {
            if (categoryName != null && !categoryName.trim().isEmpty() && viewModel.isEditMode()) {
                updateUiSafely(() -> binding.categorySelect.setPrimaryText(categoryName));
            }
        });

        viewModel.getBrandName().observe(this, brandName -> {
            if (brandName != null && !brandName.trim().isEmpty() && viewModel.isEditMode()) {
                updateUiSafely(() -> binding.brandSelect.setPrimaryText(brandName));
            }
        });

        viewModel.getUnitName().observe(this, unitName -> {
            if (unitName != null && !unitName.trim().isEmpty() && viewModel.isEditMode()) {
                updateUiSafely(() -> binding.buyUnitSelect.setPrimaryText(unitName));
            }
        });
    }

    private void setupCategoryDisplay() {
        try {
            if (binding.categorySelect.findViewById(R.id.category_name_text_view) != null) {
                android.widget.TextView categoryNameView = binding.categorySelect.findViewById(R.id.category_name_text_view);
                categoryNameView.setText("Select Category");
            }
            if (binding.categorySelect.findViewById(R.id.category_path_text_view) != null) {
                android.widget.TextView categoryPathView = binding.categorySelect.findViewById(R.id.category_path_text_view);
                categoryPathView.setText("No category selected");
            }
        } catch (Exception e) {
            Log.e("AddEditProductActivity", "Error setting up category display", e);
        }
    }

    private void setupBrandDisplay() {
        try {
            if (binding.brandSelect.findViewById(R.id.brand_name_text_view) != null) {
                android.widget.TextView brandNameView = binding.brandSelect.findViewById(R.id.brand_name_text_view);
                brandNameView.setText("Select Brand");
            }
        } catch (Exception e) {
            Log.e("AddEditProductActivity", "Error setting up brand display", e);
        }
    }

    private void observeCategorySelection() {
        viewModel.getCategoryName().observe(this, name -> {
            if (name != null && !name.isEmpty()) {
                if (binding.categorySelect.findViewById(R.id.category_name_text_view) != null) {
                    android.widget.TextView categoryNameView = binding.categorySelect.findViewById(R.id.category_name_text_view);
                    categoryNameView.setText(name);
                }
            }
        });

        viewModel.getCategoryPath().observe(this, path -> {
            if (path != null && !path.isEmpty() && viewModel.isEditMode()) {
                binding.categorySelect.setSecondaryText(path);
            }
        });
    }

    private void observeBrandSelection() {
        viewModel.getBrandName().observe(this, name -> {
            if (name != null && !name.isEmpty()) {
                if (binding.brandSelect.findViewById(R.id.brand_name_text_view) != null) {
                    android.widget.TextView brandNameView = binding.brandSelect.findViewById(R.id.brand_name_text_view);
                    brandNameView.setText(name);
                }
            }
        });
    }

    private void setupCategorySelection() {
        binding.categorySelect.setOnClickListener(v ->
                pickCategory.launch(new Intent(this, BrowseCategoryActivity.class)));
    }

    private void setupBrandSelection() {
        binding.brandSelect.setOnClickListener(v ->
                pickBrand.launch(new Intent(this, BrowseBrandActivity.class)));
    }

    private void setupUnitSelection() {
        binding.buyUnitSelect.setOnClickListener(v -> {
            Intent intent = new Intent(this, BrowseUnitActivity.class);
            intent.putExtra("selectionMode", true);
            pickUnit.launch(intent);
        });
    }

    private void setupPhotoSelector() {
        binding.photoSelector.setOnImagesChanged(uris -> {
            Log.d("AddEditProductActivity", "Images changed: " + uris.size() + " images");
            if (!isPopulatingUi) {
                markUnsavedChange();
            }
        });

        binding.photoSelector.setOnPhotoClickListener((position, uri) ->
                showPhotoPreviewDialog(position, uri));
    }

    private void setupUnsavedChangeWatchers() {
        TextWatcher watcher = new SimpleTextWatcher(() -> {
            if (!isPopulatingUi) {
                markUnsavedChange();
            }
        });

        binding.nameInput.getEditText().addTextChangedListener(watcher);
        binding.descInput.getEditText().addTextChangedListener(watcher);
        binding.barcodeInput.getEditText().addTextChangedListener(watcher);
        binding.buyPriceInput.getEditText().addTextChangedListener(watcher);
        binding.sellPriceInput.getEditText().addTextChangedListener(watcher);
        binding.buyStockInput.getEditText().addTextChangedListener(watcher);
        if (binding.sellStockInput != null) {
            binding.sellStockInput.getEditText().addTextChangedListener(watcher);
        }
    }

    private final ActivityResultLauncher<Intent> pickCategory =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> {
                if (res.getResultCode() == RESULT_OK && res.getData() != null) {
                    Intent data = res.getData();
                    String id = data.getStringExtra("categoryId");
                    String name = data.getStringExtra("categoryName");
                    String path = data.getStringExtra("pathString");
                    viewModel.setSelectedCategory(id, name, path);
                    binding.categorySelect.setPrimaryText(name);
                    binding.categorySelect.setSecondaryText(path);
                    markUnsavedChange();
                }
            });

    private final ActivityResultLauncher<Intent> pickBrand =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> {
                if (res.getResultCode() == RESULT_OK && res.getData() != null) {
                    Intent data = res.getData();
                    String id = data.getStringExtra("brandId");
                    String name = data.getStringExtra("brandName");
                    viewModel.setSelectedBrand(id, name);
                    binding.brandSelect.setPrimaryText(name);
                    markUnsavedChange();
                }
            });

    private final ActivityResultLauncher<Intent> pickUnit =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> {
                if (res.getResultCode() == RESULT_OK && res.getData() != null) {
                    Intent data = res.getData();
                    selectedUnitId = data.getStringExtra("unitId");
                    String unitName = data.getStringExtra("unitName");

                    // simpan ke ViewModel
                    viewModel.setSelectedUnit(selectedUnitId, unitName);

                    binding.buyUnitSelect.setPrimaryText(unitName);
                    markUnsavedChange();
                }
            });

    private void setupSaveButtons() {
        binding.btnSave.setOnClickListener(v -> {
            Log.d("AddEditProductActivity", "btnSave clicked - Archive mode");
            viewModel.setProductStatusForSave("archived");
            saveProduct(true);
        });

        binding.btnLive.setOnClickListener(v -> {
            Log.d("AddEditProductActivity", "btnLive clicked - Live mode");
            viewModel.setProductStatusForSave("active");
            saveProduct(false);
        });
    }

    private void setupBackPressed() {
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (viewModel != null && viewModel.isEditMode() && hasUnsavedChanges) {
                    showUnsavedChangesDialog();
                } else {
                    finish();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    private void markUnsavedChange() {
        hasUnsavedChanges = true;
    }

    private void updateUiSafely(Runnable action) {
        isPopulatingUi = true;
        action.run();
        isPopulatingUi = false;
    }

    private void showUnsavedChangesDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.unsaved_changes_title)
                .setMessage(R.string.unsaved_changes_message)
                .setPositiveButton(R.string.unsaved_changes_leave, (dialog, which) -> {
                    hasUnsavedChanges = false;
                    finish();
                })
                .setNegativeButton(R.string.unsaved_changes_stay, (dialog, which) -> dialog.dismiss())
                .setOnCancelListener(dialog -> dialog.dismiss())
                .show();
    }

    private void showPhotoPreviewDialog(int position, Uri uri) {
        DialogPhotoPreviewBinding previewBinding = DialogPhotoPreviewBinding.inflate(getLayoutInflater());
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(previewBinding.getRoot());
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        Glide.with(this)
                .load(uri)
                .into(previewBinding.previewImage);

        previewBinding.btnClose.setOnClickListener(v -> dialog.dismiss());
        previewBinding.btnCrop.setOnClickListener(v -> {
            dialog.dismiss();
            startCropForUri(uri, position);
        });

        dialog.show();
    }

    private void startCropForUri(Uri sourceUri, int position) {
        if (sourceUri == null) {
            return;
        }
        pendingCropIndex = position;
        Uri destinationUri = Uri.fromFile(new File(getCacheDir(), "crop_" + System.currentTimeMillis() + ".jpg"));

        UCrop.Options options = new UCrop.Options();
        options.setFreeStyleCropEnabled(true);
        options.setHideBottomControls(false);

        UCrop.of(sourceUri, destinationUri)
                .withOptions(options)
                .start(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == UCrop.REQUEST_CROP) {
            if (resultCode == RESULT_OK && data != null) {
                Uri resultUri = UCrop.getOutput(data);
                if (resultUri != null && pendingCropIndex != RecyclerView.NO_POSITION) {
                    binding.photoSelector.replaceImageAt(pendingCropIndex, resultUri);
                    markUnsavedChange();
                }
            } else if (resultCode == UCrop.RESULT_ERROR && data != null) {
                Throwable error = UCrop.getError(data);
                if (error != null) {
                    Log.e("AddEditProductActivity", "Crop error", error);
                }
            }
            pendingCropIndex = RecyclerView.NO_POSITION;
        }
    }



    private void saveProduct(boolean isArchive) {
        if (validateProductData()) {
            collectProductDataFromUI();

            // SELALU dapatkan semua imageUris yang ada di photo selector
            final List<Uri> finalImageUris = binding.photoSelector.getImageUris();
            Log.d("AddEditProductActivity", "Saving with " +
                    (finalImageUris != null ? finalImageUris.size() : "null") + " images");

            // Save product
            showSavingInProgress(true);

            AppDatabase.databaseWriteExecutor.execute(() -> {
                try {
                    AdminkuApplication app = (AdminkuApplication) getApplication();

                    // Collect product data
                    String name = binding.nameInput.getText().toString().trim();
                    String description = binding.descInput.getText().toString().trim();
                    String barcode = binding.barcodeInput.getText().toString().trim();
                    long buyPrice = parsePriceFromInput(binding.buyPriceInput.getText().toString().trim());
                    long sellPrice = parsePriceFromInput(binding.sellPriceInput.getText().toString().trim());
                    long stock = parseStockFromInput(binding.buyStockInput.getText().toString().trim());

                    // Create/update product
                    com.bdajaya.adminku.data.entity.Product product;

                    if (viewModel.isEditMode() && viewModel.getProductId().getValue() != null) {
                        // Edit mode
                        String productId = viewModel.getProductId().getValue();
                        product = app.getProductRepository().getProductByIdSync(productId);

                        if (product != null) {
                            product.setName(name);
                            product.setDescription(description);
                            product.setBarcode(barcode);
                            product.setBuyPrice(buyPrice);
                            product.setSellPrice(sellPrice);
                            product.setStock(stock);
                            product.setCategoryId(viewModel.getCategoryId().getValue());
                            product.setBrandId(viewModel.getBrandId().getValue());
                            product.setUnitId(viewModel.getUnitId().getValue());
                            product.setStatus(isArchive ? STATUS_ARCHIVED : STATUS_LIVE);

                            // Recalculate margin
                            if (buyPrice != 0) {
                                int margin = (int) (((double) (sellPrice - buyPrice) / buyPrice) * 100);
                                product.setMargin(margin);
                            }

                            // SELALU kirim semua gambar yang ada di photo selector
                            app.getProductRepository().updateProduct(product, finalImageUris);
                        }
                    } else {
                        // Add mode
                        product = new com.bdajaya.adminku.data.entity.Product(
                                null,
                                name,
                                description,
                                barcode,
                                viewModel.getCategoryId().getValue(),
                                viewModel.getBrandId().getValue(),
                                viewModel.getUnitId().getValue(),
                                buyPrice,
                                sellPrice,
                                calculateMargin(buyPrice, sellPrice),
                                stock,
                                isArchive ? STATUS_ARCHIVED : STATUS_LIVE,
                                System.currentTimeMillis(),
                                System.currentTimeMillis()
                        );

                        // Insert dengan images
                        app.getProductRepository().insertProduct(product, finalImageUris);
                    }

                    runOnUiThread(() -> {
                        showSavingInProgress(false);
                        String message = isArchive ? "Produk berhasil diarsipkan" : "Produk berhasil disimpan";
                        android.widget.Toast.makeText(this, "✅ " + message, android.widget.Toast.LENGTH_LONG).show();
                        setResult(RESULT_OK);
                        hasUnsavedChanges = false;
                        finish();
                    });

                } catch (Exception e) {
                    Log.e("AddEditProductActivity", "Error saving product", e);
                    runOnUiThread(() -> {
                        showSavingInProgress(false);
                        android.widget.Toast.makeText(this, "❌ Error: " + e.getMessage(),
                                android.widget.Toast.LENGTH_LONG).show();
                    });
                }
            });
        }
    }

    private static class SimpleTextWatcher implements TextWatcher {
        private final Runnable afterChange;

        private SimpleTextWatcher(Runnable afterChange) {
            this.afterChange = afterChange;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // no-op
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // no-op
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (afterChange != null) {
                afterChange.run();
            }
        }
    }

    private String getValidUnitId(AdminkuApplication app) {
        com.bdajaya.adminku.data.entity.Unit pcsUnit = app.getUnitRepository().getUnitByName("pcs");
        if (pcsUnit != null) {
            return pcsUnit.getId();
        }

        List<com.bdajaya.adminku.data.entity.Unit> units = app.getUnitRepository().getAllUnitsSync();
        if (!units.isEmpty()) {
            return units.get(0).getId();
        }

        // Create default unit
        return app.getUnitRepository().addUnit("pcs", "pcs", 1, true);
    }

    private int calculateMargin(long buyPrice, long sellPrice) {
        if (buyPrice == 0) return 0;
        return (int) (((double) (sellPrice - buyPrice) / buyPrice) * 100);
    }

    private void showSavingInProgress(boolean show) {
        binding.btnSave.setEnabled(!show);
        binding.btnLive.setEnabled(!show);

        if (show) {
            binding.btnSave.setText("Menyimpan...");
            binding.btnLive.setText("Menyimpan...");
        } else {
            binding.btnSave.setText(R.string.archive);
            binding.btnLive.setText(R.string.tab_live);
        }
    }

    private boolean validateProductData() {
        boolean isValid = true;

        if (!binding.photoSelector.validate(getString(R.string.error_photos_required))) {
            isValid = false;
        }

        if (!binding.nameInput.validate(getString(R.string.error_product_name_required))) {
            isValid = false;
        }

        if (!binding.buyPriceInput.validate(getString(R.string.error_buy_price_required))) {
            isValid = false;
        }

        if (!binding.sellPriceInput.validate(getString(R.string.error_sell_price_required))) {
            isValid = false;
        }

        if (!binding.categorySelect.validate(getString(R.string.error_category_required))) {
            isValid = false;
        }

        String categoryId = viewModel.getCategoryId().getValue();
        if (categoryId == null || categoryId.trim().isEmpty()) {
            binding.categorySelect.setError(getString(R.string.error_category_required));
            isValid = false;
        }

        if (!isValid) {
            android.widget.Toast.makeText(
                    this,
                    getString(R.string.error_fill_required_fields),
                    android.widget.Toast.LENGTH_SHORT
            ).show();
        }
        return isValid;
    }

    private void collectProductDataFromUI() {
        String name = binding.nameInput.getText().toString().trim();
        String description = binding.descInput.getText().toString().trim();
        String barcode = binding.barcodeInput.getText().toString().trim();

        String buyPriceText = binding.buyPriceInput.getText().toString().trim();
        String sellPriceText = binding.sellPriceInput.getText().toString().trim();

        long buyPrice = parsePriceFromInput(buyPriceText);
        long sellPrice = parsePriceFromInput(sellPriceText);

        String stockText = binding.buyStockInput.getText().toString().trim();
        long stock = parseStockFromInput(stockText);

        viewModel.setProductData(name, description, barcode, buyPrice, sellPrice, stock);
    }

    private long parsePriceFromInput(String priceText) {
        if (priceText.isEmpty()) return DEFAULT_PRICE;

        long price = CurrencyFormatter.parseCurrencyToLong(priceText);
        if (price > 0) return price;

        try {
            long rupiahValue = Long.parseLong(priceText.replaceAll("[^0-9]", ""));
            return rupiahValue * 100;
        } catch (NumberFormatException e) {
            return DEFAULT_PRICE;
        }
    }

    private long parseStockFromInput(String stockText) {
        if (stockText.isEmpty()) return DEFAULT_STOCK;

        try {
            return Long.parseLong(stockText.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return DEFAULT_STOCK;
        }
    }
}
