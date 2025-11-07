package com.bdajaya.adminku.ui.viewmodel;

import android.net.Uri;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import javax.inject.Inject;

import com.bdajaya.adminku.data.AppDatabase;
import com.bdajaya.adminku.data.entity.Product;

import com.bdajaya.adminku.data.entity.Unit;
import com.bdajaya.adminku.data.repository.BrandRepository;
import com.bdajaya.adminku.data.repository.CategoryRepository;
import com.bdajaya.adminku.data.repository.ProductRepository;
import com.bdajaya.adminku.data.repository.UnitRepository;

import java.util.List;


import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class AddEditProductViewModel extends ViewModel {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final UnitRepository unitRepository;
    private final MutableLiveData<String> productId = new MutableLiveData<>(null);
    // Update LiveData untuk category
    private final MutableLiveData<String> categoryId = new MutableLiveData<>(null);
    private final MutableLiveData<String> categoryName = new MutableLiveData<>("");
    private final MutableLiveData<String> categoryPath = new MutableLiveData<>("");

    // Update LiveData untuk brand
    private final MutableLiveData<String> brandId = new MutableLiveData<>(null);
    private final MutableLiveData<String> brandName = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> saveSuccess = new MutableLiveData<>(null);
    private final MutableLiveData<String> unitId = new MutableLiveData<>(null);
    private final MutableLiveData<String> unitName = new MutableLiveData<>("");
    private boolean isEditMode = false;

    // Additional fields for collecting product data from UI
    private MutableLiveData<String> productNameLiveData = new MutableLiveData<>("");
    private MutableLiveData<String> productDescriptionLiveData = new MutableLiveData<>("");
    private MutableLiveData<String> productBarcodeLiveData = new MutableLiveData<>("");
    private MutableLiveData<Long> productBuyPriceLiveData = new MutableLiveData<>(0L);
    private MutableLiveData<Long> productSellPriceLiveData = new MutableLiveData<>(0L);
    private MutableLiveData<Long> productStockLiveData = new MutableLiveData<>(0L);

    // Legacy fields kept for backward compatibility
    private String productName;
    private String productDescription;
    private String productBarcode;
    private String productStatusForSave = "active"; // Default to active/live
    private long productBuyPrice = 0;
    private long productSellPrice = 0;
    private long productStock = 0; // Default stock

    public AddEditProductViewModel(ProductRepository productRepository, CategoryRepository categoryRepository, BrandRepository brandRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.unitRepository = null; // Will be injected if available
    }

    @Inject
    public AddEditProductViewModel(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            BrandRepository brandRepository,
            UnitRepository unitRepository
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.unitRepository = unitRepository;
    }

    // Constructor for backwards compatibility
    public AddEditProductViewModel(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = null;
        this.unitRepository = null;
    }
    public void loadProduct(String id) {
        // Determine edit mode based on the passed id
        if (id == null || id.trim().isEmpty()) {
            // New product mode
            isEditMode = false;
            isLoading.setValue(false);
            // Reset all fields for new product
            productId.setValue(null);
            categoryId.setValue(null);
            categoryName.setValue("");
            brandId.setValue(null);
            brandName.setValue("");
            return;
        }

        // Edit mode
        isEditMode = true;
        isLoading.setValue(true);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                var product = productRepository.getProductByIdSync(id);
                if (product != null) {
                    productId.postValue(product.getId());
                    categoryId.postValue(product.getCategoryId());
                    brandId.postValue(product.getBrandId()); // Load brandId as well

                    // Populate product data LiveData for UI population
                    productNameLiveData.postValue(product.getName() != null ? product.getName() : "");
                    productDescriptionLiveData.postValue(product.getDescription() != null ? product.getDescription() : "");
                    productBarcodeLiveData.postValue(product.getBarcode() != null ? product.getBarcode() : "");
                    productBuyPriceLiveData.postValue(product.getBuyPrice());
                    productSellPriceLiveData.postValue(product.getSellPrice());
                    productStockLiveData.postValue(product.getStock());

                    // Load category name and path if available
                    if (product.getCategoryId() != null && !product.getCategoryId().isEmpty()) {
                        var category = categoryRepository.getCategoryByIdSync(product.getCategoryId());
                        categoryName.postValue(category != null ? category.getName() : "");

                        // Load category path to root
                        if (categoryRepository != null) {
                            var pathToRoot = categoryRepository.pathToRoot(product.getCategoryId());
                            if (pathToRoot != null && !pathToRoot.isEmpty()) {
                                StringBuilder pathBuilder = new StringBuilder();
                                for (int i = pathToRoot.size() - 1; i >= 0; i--) {
                                    pathBuilder.append(pathToRoot.get(i).getName());
                                    if (i > 0) {
                                        pathBuilder.append(" > ");
                                    }
                                }
                                categoryPath.postValue(pathBuilder.toString());
                            } else {
                                categoryPath.postValue("");
                            }
                        }
                    } else {
                        categoryName.postValue("");
                        categoryPath.postValue("");
                    }

                    // Load brand name
                    if (brandRepository != null && product.getBrandId() != null && !product.getBrandId().isEmpty()) {
                        var brand = brandRepository.getBrandByIdSync(product.getBrandId());
                        brandName.postValue(brand != null ? brand.getName() : "");
                    } else {
                        brandName.postValue("");
                    }

                    // Load unit name
                    if (unitRepository != null && product.getUnitId() != null && !product.getUnitId().isEmpty()) {
                        Unit unit = unitRepository.getUnitByIdSync(product.getUnitId());
                        unitId.postValue(product.getUnitId());
                        unitName.postValue(unit != null ? unit.getName() : "");
                    } else {
                        unitId.postValue(null);
                        unitName.postValue("");
                    }
                } else {
                    errorMessage.postValue("Product not found");
                }
            } catch (Exception e) {
                errorMessage.postValue("Error loading product: " + e.getMessage());
            } finally {
                isLoading.postValue(false);
            }
        });
    }
    // Save product
    public void saveProduct() {
        isLoading.setValue(true);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                // Create or update product
                Product product;
                String productIdToUse;

                if (isEditMode && productId.getValue() != null && !productId.getValue().isEmpty()) {
                    // Update existing product
                    productIdToUse = productId.getValue();
                    product = productRepository.getProductByIdSync(productIdToUse);
                    if (product == null) {
                        errorMessage.postValue("Product not found for update");
                        saveSuccess.postValue(false);
                        isLoading.postValue(false);
                        return;
                    }
                    // Update ALL fields from user input - ENSURE VALUES ARE USED
                    String finalName = (productName != null && !productName.trim().isEmpty()) ? productName : product.getName();
                    product.setName(finalName != null && !finalName.trim().isEmpty() ? finalName : "Unnamed Product");
                    product.setDescription(productDescription != null ? productDescription : product.getDescription());
                    product.setBarcode(productBarcode != null ? productBarcode : product.getBarcode());
                    product.setBuyPrice(productBuyPrice);
                    product.setSellPrice(productSellPrice);
                    product.setStock(productStock);
                    product.setCategoryId(categoryId.getValue());
                    product.setBrandId(brandId.getValue());
                    product.setUnitId(unitId.getValue());

                    // Recalculate margin
                    if (product.getBuyPrice() != 0) {
                        int margin = (int) (((double) (product.getSellPrice() - product.getBuyPrice()) / product.getBuyPrice()) * 100);
                        product.setMargin(margin);
                    }

                    // Set status based on productStatusForSave
                    String status = "archived".equalsIgnoreCase(productStatusForSave) ? "ARCHIVED" : "LIVE";
                    product.setStatus(status);

                    product.setUpdatedAt(System.currentTimeMillis());
                    // Update via repository
                    updateExistingProduct(product, null); // No images for now
                } else {
                    // Create new product with default values
                    product = createNewProductWithDefaults();
                    productIdToUse = productRepository.insertProduct(product, null); // No images
                }

                // Success
                saveSuccess.postValue(true);
                errorMessage.postValue("");
                // Optionally set the productId for new products after saving
                if (!isEditMode || productId.getValue() == null || productId.getValue().isEmpty()) {
                    productId.postValue(productIdToUse);
                    // Switch to edit mode after creating new product
                    isEditMode = true;
                }

            } catch (Exception e) {
                Log.e("AddEditProductViewModel", "Error saving product", e);
                errorMessage.postValue("Failed to save product: " + e.getMessage());
                saveSuccess.postValue(false);
            } finally {
                isLoading.postValue(false);
            }
        });
    }

    private Product createNewProductWithDefaults() {
        String name = productName != null && !productName.trim().isEmpty() ? productName : "Nama Produk";
        String description = productDescription != null ? productDescription : "Deskripsi";
        String barcode = productBarcode != null && !productBarcode.trim().isEmpty() ? productBarcode : "";
        // Map status: "active" → "LIVE", "archived" → "ARCHIVED"
        String status = "archived".equalsIgnoreCase(productStatusForSave) ? "ARCHIVED" : "LIVE";

        // Get a valid unit ID from the database
        String unitId = getValidUnitId();

        // Calculate margin from buy and sell prices
        int margin = 0;
        if (productBuyPrice != 0) {
            margin = (int) (((double) (productSellPrice - productBuyPrice) / productBuyPrice) * 100);
        }

        return new Product(
                null,
                name,
                description,
                barcode,
                categoryId.getValue(),
                brandId.getValue(),
                unitId,
                productBuyPrice,
                productSellPrice,
                margin,
                productStock,
                status,
                System.currentTimeMillis(),
                System.currentTimeMillis()
        );
    }

    private String getValidUnitId() {
        // Since we need synchronous database access in the write executor context,
        // we'll try to get a valid unit. If unitRepository is null, create a valid unit dynamically.

        if (unitRepository != null) {
            // Try to get existing "pcs" unit
            Unit pcsUnit = unitRepository.getUnitByName("pcs");
            if (pcsUnit != null) {
                return pcsUnit.getId();
            }

            // If not found, try to get any base unit
            List<Unit> baseUnits = unitRepository.getBaseUnits();
            if (!baseUnits.isEmpty()) {
                return baseUnits.get(0).getId();
            }

            // If not found, get any unit
            List<Unit> allUnits = unitRepository.getAllUnitsSync();
            if (!allUnits.isEmpty()) {
                return allUnits.get(0).getId();
            }

            // Last resort: create a pcs unit synchronously
            Log.w("AddEditProductViewModel", "No units found, creating default pcs unit");
            try {
                String unitId = unitRepository.addUnit("pcs", "pcs", 1, true);
                if (unitId != null) {
                    return unitId;
                }
            } catch (Exception e) {
                Log.e("AddEditProductViewModel", "Failed to create default unit", e);
            }
        }

        // If all attempts fail, return a placeholder (this will still cause FK error)
        Log.w("AddEditProductViewModel", "Unable to find or create a valid unit - foreign key constraint will fail");
        return "default_pcs_unit";
    }

    private void updateExistingProduct(Product product, List<Uri> imageUriList) {
        productRepository.updateProduct(product, imageUriList);
    }

    public void setSelectedCategory(String categoryId, String categoryName, String pathString) {
        this.categoryId.setValue(categoryId);
        this.categoryName.setValue(categoryName);
        this.categoryPath.setValue(pathString);
    }

    // Getter untuk category name
    public LiveData<String> getCategoryName() {
        return categoryName;
    }

    // Getter untuk category ID
    public LiveData<String> getCategoryId() {
        return categoryId;
    }

    // Getter untuk category ID
    public LiveData<String> getCategoryPath() {
        return categoryPath;
    }

    public void setSelectedBrand(String brandId, String brandName) {
        this.brandId.setValue(brandId);
        this.brandName.setValue(brandName);
    }

    // Getter untuk brand name
    public LiveData<String> getBrandName() {
        return brandName;
    }

    // Getter untuk brand ID
    public LiveData<String> getBrandId() {
        return brandId;
    }

    // Methods to set product data from UI
    public void setProductData(String name, String description, String barcode) {
        this.productName = name;
        this.productDescription = description;
        this.productBarcode = barcode;
    }

    // Overloaded method to set complete product data including pricing and stock
    public void setProductData(String name, String description, String barcode, long buyPrice, long sellPrice, long stock) {
        this.productName = name;
        this.productDescription = description;
        this.productBarcode = barcode;
        this.productBuyPrice = buyPrice;
        this.productSellPrice = sellPrice;
        this.productStock = stock;
    }

    // Method to set the status for saving
    public void setProductStatusForSave(String status) {
        this.productStatusForSave = status;
    }

    // LiveData getters for UI observation
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<Boolean> getSaveSuccess() {
        return saveSuccess;
    }

    // Clear save results after consumption to allow subsequent saves
    public void clearSaveResults() {
        saveSuccess.setValue(true);
        errorMessage.setValue(null);
    }

    // Reset to initial state for fresh save operation
    public void resetForNewSave() {
        saveSuccess.setValue(true);
        errorMessage.setValue(null);
        isLoading.setValue(false);
    }

    // Method setter dan getter untuk Unit
    public void setSelectedUnit(String unitId, String unitName) {
        this.unitId.setValue(unitId);
        this.unitName.setValue(unitName);
    }

    public LiveData<String> getUnitName() {
        return unitName;
    }

    public LiveData<String> getUnitId() {
        return unitId;
    }

    public LiveData<String> getProductId() {
        return productId;
    }

    public boolean isEditMode() {
        return isEditMode;
    }

    // LiveData getters for product data fields
    public LiveData<String> getProductName() {
        return productNameLiveData;
    }

    public LiveData<String> getProductDescription() {
        return productDescriptionLiveData;
    }

    public LiveData<String> getProductBarcode() {
        return productBarcodeLiveData;
    }

    public LiveData<Long> getProductBuyPrice() {
        return productBuyPriceLiveData;
    }

    public LiveData<Long> getProductSellPrice() {
        return productSellPriceLiveData;
    }

    public LiveData<Long> getProductStock() {
        return productStockLiveData;
    }
}
