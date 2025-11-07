package com.bdajaya.adminku.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bdajaya.adminku.R;
import com.bdajaya.adminku.data.model.ProductWithDetails;
import com.bdajaya.adminku.ui.activities.ProductManagementActivity;
import com.bdajaya.adminku.ui.adapter.ProductAdapter;
import com.bdajaya.adminku.ui.viewmodel.ProductManagementViewModel;
import com.facebook.shimmer.ShimmerFrameLayout;

import java.util.ArrayList;
import java.util.List;

public class ProductListFragment extends Fragment {

    private static final String ARG_STATUS = "status";

    private String status;
    private ProductManagementViewModel viewModel;
    private RecyclerView recyclerView;
    private ProductAdapter adapter;
    private ShimmerFrameLayout shimmerLayout;
    private View emptyView;

    public static ProductListFragment newInstance(String status) {
        ProductListFragment fragment = new ProductListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_STATUS, status);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            status = getArguments().getString(ARG_STATUS);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_product_list, container, false);

        recyclerView = view.findViewById(R.id.recycler_view);
        shimmerLayout = view.findViewById(R.id.shimmer_layout);
        emptyView = view.findViewById(R.id.empty_view);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Set up RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ProductAdapter(new ArrayList<>(),
                product -> ((ProductManagementActivity) requireActivity()).onProductClick(product),
                product -> ((ProductManagementActivity) requireActivity()).onProductLongClick(product),
                ((ProductManagementActivity) requireActivity()).getImageStorageManager());
        recyclerView.setAdapter(adapter);

        // Get ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(ProductManagementViewModel.class);

        // Start shimmer effect
        shimmerLayout.setVisibility(View.VISIBLE);
        shimmerLayout.startShimmer();

        // Observe products based on status
        observeProducts();
    }

    private void observeProducts() {
        switch (status) {
            case "LIVE":
                viewModel.getLiveProducts().observe(getViewLifecycleOwner(), this::updateProductList);
                break;
            case "OUT_OF_STOCK":
                viewModel.getOutOfStockProducts().observe(getViewLifecycleOwner(), this::updateProductList);
                break;
            case "ARCHIVED":
                viewModel.getArchivedProducts().observe(getViewLifecycleOwner(), this::updateProductList);
                break;
        }
    }

    private void updateProductList(List<ProductWithDetails> products) {
        // Stop shimmer and hide it
        shimmerLayout.stopShimmer();
        shimmerLayout.setVisibility(View.GONE);

        ProductManagementActivity activity = (ProductManagementActivity) requireActivity();
        List<ProductWithDetails> normalized = activity.sortProductImagesForDisplay(products);

        // Update adapter
        adapter.updateData(normalized);

        // Show empty view if needed
        emptyView.setVisibility(normalized.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
