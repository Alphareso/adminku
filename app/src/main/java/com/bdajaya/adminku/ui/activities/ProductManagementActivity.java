package com.bdajaya.adminku.ui.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.bdajaya.adminku.AdminkuApplication;
import com.bdajaya.adminku.R;
import com.bdajaya.adminku.core.Constants;
import com.bdajaya.adminku.core.ErrorHandler;
import com.bdajaya.adminku.data.AppDatabase;
import com.bdajaya.adminku.data.entity.Product;
import com.bdajaya.adminku.data.entity.Unit;
import com.bdajaya.adminku.data.manager.ImageStorageManager;
import com.bdajaya.adminku.data.model.ProductWithDetails;
import com.bdajaya.adminku.databinding.ActivityProductManagementBinding;
import com.bdajaya.adminku.databinding.DialogPriceStockBinding;
import com.bdajaya.adminku.ui.adapter.ProductTabAdapter;
import com.bdajaya.adminku.ui.fragments.ConfirmationDialog;
import com.bdajaya.adminku.ui.viewmodel.ProductManagementViewModel;
import com.bdajaya.adminku.util.CurrencyFormatter;
import com.bdajaya.adminku.util.ProductShareHelper;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.tabs.TabLayoutMediator;
import dagger.hilt.android.AndroidEntryPoint;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@AndroidEntryPoint
public class ProductManagementActivity extends AppCompatActivity {

    private ActivityProductManagementBinding binding;
    private ProductManagementViewModel viewModel;
    private ProductTabAdapter tabAdapter;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private static final long SEARCH_DELAY_MS = 300;

    // Price calculation constants
    private static final int MIN_MARGIN_PERCENT = 0;
    private static final int MAX_MARGIN_PERCENT = 1000;
    private static final long MIN_PRICE_CENTS = 0;
    private static final long MAX_STOCK_QUANTITY = 999999;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (viewModel.isSearching().getValue() != null && viewModel.isSearching().getValue()) {
                    binding.searchEditText.setText("");
                    viewModel.clearSearch();
                } else {
                    finish();
                }
            }
        });

        binding = ActivityProductManagementBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.product_management);
        }

        setupViewModel();
        setupViewPager();
        setupSearchView();
        setupAddButton();

        observeViewModel();
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(ProductManagementViewModel.class);
    }

    private void setupViewPager() {
        tabAdapter = new ProductTabAdapter(this);
        binding.viewPager.setAdapter(tabAdapter);

        new TabLayoutMediator(binding.tabLayout, binding.viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText(R.string.tab_live);
                    break;
                case 1:
                    tab.setText(R.string.tab_out_of_stock);
                    break;
                case 2:
                    tab.setText(R.string.tab_archived);
                    break;
            }
        }).attach();

        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                if (viewModel.isSearching().getValue() != null && viewModel.isSearching().getValue()) {
                    binding.searchEditText.setText("");
                    viewModel.clearSearch();
                }
            }
        });
    }

    private void setupSearchView() {
        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchHandler.removeCallbacksAndMessages(null);

                if (s.length() > 0) {
                    searchHandler.postDelayed(() -> viewModel.search(s.toString()), SEARCH_DELAY_MS);
                } else {
                    viewModel.clearSearch();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        binding.clearSearchButton.setOnClickListener(v -> {
            binding.searchEditText.setText("");
            viewModel.clearSearch();
        });
    }

    private void setupAddButton() {
        binding.btnAddProduct.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddEditProductActivity.class);
            addProductLauncher.launch(intent);
        });
    }

    private void observeViewModel() {
        viewModel.isSearching().observe(this, isSearching -> {
            binding.clearSearchButton.setVisibility(isSearching ? View.VISIBLE : View.GONE);
            binding.tabLayout.setVisibility(isSearching ? View.GONE : View.VISIBLE);
            binding.viewPager.setVisibility(isSearching ? View.GONE : View.VISIBLE);
            binding.recyclerViewSearchResults.setVisibility(isSearching ? View.VISIBLE : View.GONE);
            binding.emptySearchView.setVisibility(isSearching &&
                    (viewModel.getSearchResults().getValue() == null ||
                            viewModel.getSearchResults().getValue().isEmpty()) ?
                    View.VISIBLE : View.GONE);
        });

        viewModel.getSearchResults().observe(this, results -> {
            if (Boolean.TRUE.equals(viewModel.isSearching().getValue())) {
                List<ProductWithDetails> normalized = sortProductImagesForDisplay(results);
                tabAdapter.updateSearchResults(normalized);
                binding.emptySearchView.setVisibility(normalized.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.isLoading().observe(this, isLoading -> {
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });

        viewModel.getErrorMessage().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private final ActivityResultLauncher<Intent> addProductLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), res -> {
                if (res.getResultCode() == RESULT_OK) {
                    if (Boolean.TRUE.equals(viewModel.isSearching().getValue())) {
                        viewModel.search(viewModel.getSearchQuery().getValue());
                    }
                }
            });

    private final ActivityResultLauncher<Intent> editProductLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), res -> {
                if (res.getResultCode() == RESULT_OK) {
                    if (Boolean.TRUE.equals(viewModel.isSearching().getValue())) {
                        viewModel.search(viewModel.getSearchQuery().getValue());
                    }
                }
            });

    public void onProductClick(ProductWithDetails product) {
        Intent intent = new Intent(this, AddEditProductActivity.class);
        intent.putExtra("productId", product.product.getId());
        editProductLauncher.launch(intent);
    }

    public void onProductLongClick(ProductWithDetails product) {
        showProductOptionsBottomSheet(product);
    }

    private void showProductOptionsBottomSheet(ProductWithDetails product) {
        View bottomSheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_product_options, null);
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        bottomSheetDialog.setContentView(bottomSheetView);

        bottomSheetView.findViewById(R.id.option_share).setOnClickListener(v -> {
            shareProduct(product);
            bottomSheetDialog.dismiss();
        });

        bottomSheetView.findViewById(R.id.option_archive).setOnClickListener(v -> {
            if ("ARCHIVED".equals(product.product.getStatus())) {
                viewModel.unarchiveProduct(product.product.getId(), product.product.getStock());
            } else {
                viewModel.archiveProduct(product.product.getId());
            }
            bottomSheetDialog.dismiss();
        });

        bottomSheetView.findViewById(R.id.option_edit).setOnClickListener(v -> {
            Intent intent = new Intent(this, AddEditProductActivity.class);
            intent.putExtra("productId", product.product.getId());
            editProductLauncher.launch(intent); // Ganti dari startActivityForResult
            bottomSheetDialog.dismiss();
        });

        bottomSheetView.findViewById(R.id.option_price_stock).setOnClickListener(v -> {
            showPriceStockDialog(product);
            bottomSheetDialog.dismiss();
        });

        bottomSheetView.findViewById(R.id.option_copy).setOnClickListener(v -> {
            String productDetails = product.product.getName() + "\n" +
                    product.product.getBarcode() + "\n" +
                    CurrencyFormatter.formatCurrency(product.product.getSellPrice()) + "\n" +
                    product.product.getStock() + " " + product.getUnitName();

            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Product Details", productDetails);
            clipboard.setPrimaryClip(clip);

            Toast.makeText(this, "Product details copied to clipboard", Toast.LENGTH_SHORT).show();
            bottomSheetDialog.dismiss();
        });

        bottomSheetView.findViewById(R.id.option_delete).setOnClickListener(v -> {
            showDeleteConfirmationDialog(product.product);
            bottomSheetDialog.dismiss();
        });

        bottomSheetView.findViewById(R.id.option_cancel).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
        });

        if ("ARCHIVED".equals(product.product.getStatus())) {
            ((android.widget.TextView) bottomSheetView.findViewById(R.id.option_archive_text))
                    .setText(R.string.show);
        } else {
            ((android.widget.TextView) bottomSheetView.findViewById(R.id.option_archive_text))
                    .setText(R.string.archive);
        }

        bottomSheetDialog.show();
    }

    private void showPriceStockDialog(ProductWithDetails pwd) {
        DialogPriceStockBinding dialogBinding = DialogPriceStockBinding.inflate(getLayoutInflater());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(pwd.product.getName())
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.save, null) // Set null first, override later
                .setNegativeButton(R.string.cancel, (d, w) -> d.dismiss())
                .create();

        // Load units for spinner
        loadUnitsForDialog(dialogBinding, pwd);

        initializeDialogValues(dialogBinding, pwd);
        setupPriceCalculationListeners(dialogBinding);

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (applyQuickEdit(pwd, dialogBinding)) {
                    dialog.dismiss();
                }
            });
        });

        dialog.show();
    }

    private void loadUnitsForDialog(DialogPriceStockBinding binding, ProductWithDetails pwd) {
        AdminkuApplication app = (AdminkuApplication) getApplication();

        AppDatabase.databaseWriteExecutor.execute(() -> {
            // Get current unit
            Unit currentUnit = app.getUnitRepository().getUnitByIdSync(pwd.product.getUnitId());

            // Get compatible units (same base unit)
            List<Unit> compatibleUnits;
            if (currentUnit != null) {
                compatibleUnits = app.getUnitRepository().getUnitsByBaseUnit(currentUnit.getBaseUnit());
            } else {
                compatibleUnits = app.getUnitRepository().getAllUnitsSync();
            }

            runOnUiThread(() -> {
                List<String> unitNames = new ArrayList<>();
                List<String> unitIds = new ArrayList<>();
                int selectedIndex = 0;

                for (int i = 0; i < compatibleUnits.size(); i++) {
                    Unit unit = compatibleUnits.get(i);
                    unitNames.add(unit.getDisplayText());
                    unitIds.add(unit.getId());

                    if (unit.getId().equals(pwd.product.getUnitId())) {
                        selectedIndex = i;
                    }
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_item,
                        unitNames
                );
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                binding.unitSpinner.setAdapter(adapter);
                binding.unitSpinner.setSelection(selectedIndex);
                binding.unitSpinner.setTag(unitIds); // Store unit IDs
            });
        });
    }

    private void initializeDialogValues(DialogPriceStockBinding binding, ProductWithDetails pwd) {
        binding.buyPriceInput.setCurrencyValue(pwd.product.getBuyPrice());
        binding.sellPriceInput.setCurrencyValue(pwd.product.getSellPrice());
        binding.marginInput.setValue(String.valueOf(pwd.product.getMargin()));

        // Convert stock to display in current unit - run on background thread
        AdminkuApplication app = (AdminkuApplication) getApplication();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Unit currentUnit = app.getUnitRepository().getUnitByIdSync(pwd.product.getUnitId());

            runOnUiThread(() -> {
                if (currentUnit != null) {
                    long displayStock = currentUnit.fromBaseUnit(pwd.product.getStock());
                    binding.stockInput.setValue(String.valueOf(displayStock));
                } else {
                    binding.stockInput.setValue(String.valueOf(pwd.product.getStock()));
                }
            });
        });
    }

    private void setupPriceCalculationListeners(DialogPriceStockBinding binding) {
        final boolean[] isUpdating = {false};

        binding.buyPriceInput.setOnValueChangedListener(value -> {
            if (isUpdating[0] || !(value instanceof Long)) return;
            updateSellPriceFromBuyPriceAndMargin(binding, isUpdating, (Long) value);
        });

        binding.sellPriceInput.setOnValueChangedListener(value -> {
            if (isUpdating[0] || !(value instanceof Long)) return;
            updateMarginFromBuyAndSellPrice(binding, isUpdating, (Long) value);
        });

        binding.marginInput.setOnValueChangedListener(value -> {
            if (isUpdating[0] || !(value instanceof String)) return;
            updateSellPriceFromMargin(binding, isUpdating, (String) value);
        });
    }

    private void updateSellPriceFromBuyPriceAndMargin(DialogPriceStockBinding binding,
                                                      boolean[] isUpdating,
                                                      long buyPrice) {
        isUpdating[0] = true;
        try {
            int margin = safeInt(binding.marginInput.getValue(), 0);
            long sellPrice = computeSellFromBuyAndMargin(buyPrice, margin);
            binding.sellPriceInput.setCurrencyValue(sellPrice);
        } finally {
            isUpdating[0] = false;
        }
    }

    private void updateMarginFromBuyAndSellPrice(DialogPriceStockBinding binding,
                                                 boolean[] isUpdating,
                                                 long sellPrice) {
        isUpdating[0] = true;
        try {
            long buyPrice = binding.buyPriceInput.getCurrencyValue();
            int margin = computeMarginPercent(buyPrice, sellPrice);
            binding.marginInput.setValue(String.valueOf(margin));
        } finally {
            isUpdating[0] = false;
        }
    }

    private void updateSellPriceFromMargin(DialogPriceStockBinding binding,
                                           boolean[] isUpdating,
                                           String marginText) {
        isUpdating[0] = true;
        try {
            int margin = clamp(safeInt(marginText, 0), MIN_MARGIN_PERCENT, MAX_MARGIN_PERCENT);
            long buyPrice = binding.buyPriceInput.getCurrencyValue();
            long sellPrice = computeSellFromBuyAndMargin(buyPrice, margin);
            binding.sellPriceInput.setCurrencyValue(sellPrice);
        } finally {
            isUpdating[0] = false;
        }
    }

    private boolean applyQuickEdit(ProductWithDetails pwd, DialogPriceStockBinding b) {
        long buyPrice = Math.max(MIN_PRICE_CENTS, b.buyPriceInput.getCurrencyValue());
        long sellPrice = Math.max(MIN_PRICE_CENTS, b.sellPriceInput.getCurrencyValue());
        int margin = clamp(safeInt(b.marginInput.getValue(), 0), MIN_MARGIN_PERCENT, MAX_MARGIN_PERCENT);

        // Get selected unit
        @SuppressWarnings("unchecked")
        List<String> unitIds = (List<String>) b.unitSpinner.getTag();
        if (unitIds == null || unitIds.isEmpty()) {
            Toast.makeText(this, "Error: Unit not found", Toast.LENGTH_SHORT).show();
            return false;
        }

        int selectedPosition = b.unitSpinner.getSelectedItemPosition();
        String selectedUnitId = unitIds.get(selectedPosition);

        long inputQuantity = clampLong(safeLong(b.stockInput.getValue(), 0), 0, MAX_STOCK_QUANTITY);

        if (sellPrice <= buyPrice) {
            Toast.makeText(this, "Sell price must be higher than buy price", Toast.LENGTH_SHORT).show();
            return false;
        }

        AdminkuApplication app = (AdminkuApplication) getApplication();

        // Run database operations on background thread
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                // Get units
                Unit selectedUnit = app.getUnitRepository().getUnitByIdSync(selectedUnitId);
                Unit oldUnit = app.getUnitRepository().getUnitByIdSync(pwd.product.getUnitId());

                if (selectedUnit == null) {
                    runOnUiThread(() -> Toast.makeText(this, "Error: Selected unit not found", Toast.LENGTH_SHORT).show());
                    return;
                }

                Product p = pwd.product;

                // Convert input quantity to base unit for new stock
                long newStockInBaseUnit = selectedUnit.toBaseUnit(inputQuantity);

                // Get old stock in base unit
                long oldStockInBaseUnit = p.getStock();

                // Calculate delta in base unit
                long deltaInBaseUnit = newStockInBaseUnit - oldStockInBaseUnit;

                // Update product
                p.setBuyPrice(buyPrice);
                p.setSellPrice(sellPrice);
                p.setMargin(margin);
                p.setStock(newStockInBaseUnit); // Store in base unit
                p.setUnitId(selectedUnitId); // Update unit if changed

                if (!"ARCHIVED".equals(p.getStatus())) {
                    p.setStatus(newStockInBaseUnit > 0 ? "LIVE" : "OUT_OF_STOCK");
                }

                // Update product in database
                app.getProductRepository().updateProduct(p, null);

                // Add stock transaction if there's a change
                if (deltaInBaseUnit != 0) {
                    String notes = "Quick edit";
                    if (!selectedUnitId.equals(pwd.product.getUnitId())) {
                        notes += " (unit changed from " +
                                (oldUnit != null ? oldUnit.getName() : "unknown") +
                                " to " + selectedUnit.getName() + ")";
                    }

                    if (deltaInBaseUnit > 0) {
                        app.getStockRepository().addStock(
                                p.getId(),
                                deltaInBaseUnit, // Already in base unit
                                selectedUnitId,
                                notes
                        );
                    } else {
                        app.getStockRepository().removeStock(
                                p.getId(),
                                Math.abs(deltaInBaseUnit), // Already in base unit
                                selectedUnitId,
                                notes
                        );
                    }
                }

                runOnUiThread(() -> {
                    Toast.makeText(this, R.string.success, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                android.util.Log.e("ProductManagementActivity", "Error in applyQuickEdit", e);
                runOnUiThread(() -> Toast.makeText(this, "Error updating product", Toast.LENGTH_SHORT).show());
            }
        });

        return true; // Return true to dismiss dialog, actual success/failure shown in background
    }

    private static long safeLong(String s, long def) {
        try {
            return Long.parseLong(s.replaceAll("[^0-9-]", ""));
        } catch (Exception e) {
            return def;
        }
    }

    private void showDeleteConfirmationDialog(Product product) {
        try {
            ConfirmationDialog dialog = ConfirmationDialog.newInstanceForProduct(
                    product.getId(),
                    product.getName()
            );

            dialog.setOnConfirmationActionListener(new ConfirmationDialog.OnConfirmationActionListener() {
                @Override
                public void onConfirm(Object data) {
                    if (data instanceof ConfirmationDialog.ProductSimpleData) {
                        ConfirmationDialog.ProductSimpleData productData =
                                (ConfirmationDialog.ProductSimpleData) data;
                        // Delete product using productData.id
                        viewModel.deleteProduct(product); // atau gunakan productData.id
                        Toast.makeText(ProductManagementActivity.this,
                                R.string.success, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onCancel() {
                    // Clean up
                }
            });

            dialog.show(getSupportFragmentManager(), "ConfirmationDialog");

        } catch (Exception e) {
            ErrorHandler.logError(ErrorHandler.ERROR_CODE_UNKNOWN,
                    "Error showing confirmation dialog", e);
            Toast.makeText(this, Constants.ERROR_UNEXPECTED, Toast.LENGTH_SHORT).show();
        }
    }

    private void shareProduct(ProductWithDetails product) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AdminkuApplication app = (AdminkuApplication) getApplication();
            List<File> imageFiles = app.getProductRepository()
                    .getProductImageFilesForSharing(product.product.getId());

            runOnUiThread(() -> {
                ProductShareHelper.ShareCallback shareCallback = new ProductShareHelper.ShareCallback() {
                    @Override
                    public void onShareStarted() {
                        Toast.makeText(ProductManagementActivity.this,
                                "Memulai share...", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onShareCompleted() {
                        Toast.makeText(ProductManagementActivity.this,
                                "Share berhasil! Cache akan dibersihkan otomatis.", Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onShareFailed(String error) {
                        Toast.makeText(ProductManagementActivity.this,
                                "Share gagal: " + error, Toast.LENGTH_LONG).show();
                    }
                };

                if (ProductShareHelper.isWhatsAppInstalled(this)) {
                    new AlertDialog.Builder(this)
                            .setTitle("Share Product")
                            .setMessage("Bagikan ke mana?")
                            .setPositiveButton("WhatsApp", (d, w) ->
                                    ProductShareHelper.shareToWhatsAppWithCallback(this, product.product, imageFiles, shareCallback))
                            .setNegativeButton("Lainnya", (d, w) ->
                                    ProductShareHelper.shareProductWithCallback(this, product.product, imageFiles, shareCallback))
                            .setNeutralButton("Batal", null)
                            .show();
                } else {
                    ProductShareHelper.shareProductWithCallback(this, product.product, imageFiles, shareCallback);
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AdminkuApplication app = (AdminkuApplication) getApplication();
        app.getProductRepository().cleanupSharingCache();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_product_management, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        if (item.getItemId() == R.id.action_settings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static int computeMarginPercent(long buyCents, long sellCents) {
        if (buyCents <= 0) return 0;
        double pct = ((double)(sellCents - buyCents) / (double)buyCents) * 100.0;
        return (int) Math.round(pct);
    }

    private static long computeSellFromBuyAndMargin(long buyCents, int marginPct) {
        if (buyCents <= 0) return 0L;
        double sell = buyCents * (1.0 + (marginPct / 100.0));
        return Math.round(sell);
    }

    private static int safeInt(String s, int def) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9-]", ""));
        } catch (Exception e) {
            return def;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static long clampLong(long v, long lo, long hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public ImageStorageManager getImageStorageManager() {
        AdminkuApplication app = (AdminkuApplication) getApplication();
        return app.getImageStorageManager();
    }

    public List<ProductWithDetails> sortProductImagesForDisplay(List<ProductWithDetails> products) {
        if (products == null) {
            return new ArrayList<>();
        }
        List<ProductWithDetails> normalized = new ArrayList<>(products.size());
        for (ProductWithDetails product : products) {
            if (product == null) {
                continue;
            }
            if (product.images != null && product.images.size() > 1) {
                product.images.sort((img1, img2) ->
                        Integer.compare(img1.getOrderIndex(), img2.getOrderIndex()));
            }
            normalized.add(product);
        }
        return normalized;
    }
}
