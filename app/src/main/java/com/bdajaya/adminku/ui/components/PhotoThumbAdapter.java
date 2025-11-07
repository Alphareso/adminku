package com.bdajaya.adminku.ui.components;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.bdajaya.adminku.R;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class PhotoThumbAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = "PhotoThumbAdapter";

    private final List<Uri> imageUris = new ArrayList<>();
    private Runnable onAddClick;
    private OnRemove onRemove;
    private OnOrderChanged onOrderChanged;
    private OnImageClick onImageClick;
    private ItemTouchHelper itemTouchHelper;

    public interface OnRemove {
        void onClick(int position);
    }

    public interface OnOrderChanged {
        void onOrderChanged(List<Uri> newOrder);
    }

    public interface OnImageClick {
        void onClick(int position, Uri uri);
    }

    private static final int TYPE_ADD = 0;
    private static final int TYPE_ITEM = 1;

    public void setOnAddClick(Runnable runnable) {
        this.onAddClick = runnable;
    }

    public void setOnRemoveClick(OnRemove onRemove) {
        this.onRemove = onRemove;
    }

    public void setOnOrderChanged(OnOrderChanged onOrderChanged) {
        this.onOrderChanged = onOrderChanged;
    }

    public void setOnImageClick(OnImageClick onImageClick) {
        this.onImageClick = onImageClick;
    }

    public void setItemTouchHelper(ItemTouchHelper itemTouchHelper) {
        this.itemTouchHelper = itemTouchHelper;
    }

    public void addBase64Images(List<String> images) {
        if (images == null) return;
        for (String img : images) {
            imageUris.add(Uri.parse(img));
        }
        notifyDataSetChanged();
    }

    public void replaceImages(List<String> images) {
        imageUris.clear();
        if (images != null) {
            for (String img : images) {
                imageUris.add(Uri.parse(img));
            }
        }
        notifyDataSetChanged();
    }

    public void addUris(List<Uri> uris) {
        this.imageUris.addAll(Objects.requireNonNull(uris));
        notifyDataSetChanged();
    }

    public void replaceUris(List<Uri> uris) {
        imageUris.clear();
        if (uris != null) {
            imageUris.addAll(uris);
        }
        notifyDataSetChanged();
    }

    public void removeImage(int position) {
        if (position >= 0 && position < imageUris.size()) {
            imageUris.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, imageUris.size() - position + 1);
        }
    }

    public void updateUri(int position, Uri uri) {
        if (position < 0 || position >= imageUris.size() || uri == null) {
            return;
        }

        imageUris.set(position, uri);
        notifyItemChanged(position);
    }

    public void moveItem(int fromPosition, int toPosition) {
        if (fromPosition < 0 || fromPosition >= imageUris.size() ||
                toPosition < 0 || toPosition >= imageUris.size()) {
            return;
        }

        Collections.swap(imageUris, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);

        if (onOrderChanged != null) {
            onOrderChanged.onOrderChanged(new ArrayList<>(imageUris));
        }
    }

    public ItemTouchHelper.Callback getItemTouchHelperCallback() {
        return new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                        @NonNull RecyclerView.ViewHolder viewHolder) {
                if (viewHolder.getItemViewType() == TYPE_ADD) {
                    return 0;
                }
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN |
                        ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                // Use getBindingAdapterPosition() instead of deprecated getAdapterPosition()
                int fromPosition = viewHolder.getBindingAdapterPosition();
                int toPosition = target.getBindingAdapterPosition();

                if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION) {
                    return false;
                }

                // Don't allow moving to/from the add button position
                if (fromPosition == imageUris.size() || toPosition == imageUris.size()) {
                    return false;
                }

                moveItem(fromPosition, toPosition);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // No swipe action needed
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return false;
            }
        };
    }

    public List<String> getBase64Images() {
        List<String> result = new ArrayList<>();
        for (Uri uri : imageUris) {
            result.add(uri.toString());
        }
        return result;
    }

    public List<Uri> getImageUris() {
        return new ArrayList<>(imageUris);
    }

    public int getItemCountWithoutAdd() {
        return imageUris.size();
    }

    @Override
    public int getItemViewType(int position) {
        return position == imageUris.size() ? TYPE_ADD : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_photo_thumb, parent, false);
        if (viewType == TYPE_ADD) {
            return new AddViewHolder(view);
        } else {
            return new ImageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AddViewHolder) {
            ((AddViewHolder) holder).bind(onAddClick);
        } else if (holder instanceof ImageViewHolder) {
            Uri uri = imageUris.get(position);
            ((ImageViewHolder) holder).bind(uri, onRemove, onImageClick, itemTouchHelper);
        }
    }

    @Override
    public int getItemCount() {
        return imageUris.size() + 1;
    }

    static class AddViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imageView;
        private final View removeButton;

        AddViewHolder(View view) {
            super(view);
            imageView = view.findViewById(R.id.img);
            removeButton = view.findViewById(R.id.btnRemove);
        }

        void bind(Runnable onAddClick) {
            imageView.setImageResource(R.drawable.ic_add_photo);
            imageView.setContentDescription("Add photo");
            imageView.setPadding(32, 32, 32, 32);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            removeButton.setVisibility(View.GONE);
            itemView.setOnClickListener(v -> {
                if (onAddClick != null) {
                    onAddClick.run();
                }
            });
        }
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imageView;
        private final View removeButton;
        private final View dragSurface;

        ImageViewHolder(View view) {
            super(view);
            imageView = view.findViewById(R.id.img);
            removeButton = view.findViewById(R.id.btnRemove);
            dragSurface = view.findViewById(R.id.btnDrag);
        }

        void bind(Uri uri,
                  OnRemove onRemove,
                  OnImageClick onImageClick,
                  ItemTouchHelper itemTouchHelper) {
            Glide.with(imageView).clear(imageView);
            Glide.with(imageView.getContext())
                    .load(uri)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_products)
                    .centerCrop()
                    .into(imageView);

            int adapterPosition = getBindingAdapterPosition();
            int displayPosition = adapterPosition == RecyclerView.NO_POSITION ? 0 : adapterPosition;
            imageView.setContentDescription("Photo " + (displayPosition + 1));
            removeButton.setVisibility(View.VISIBLE);
            dragSurface.setVisibility(View.VISIBLE);

            removeButton.setOnClickListener(v -> {
                if (onRemove != null) {
                    int currentPosition = getBindingAdapterPosition();
                    if (currentPosition != RecyclerView.NO_POSITION) {
                        onRemove.onClick(currentPosition);
                    }
                }
            });

            imageView.setOnClickListener(v -> {
                if (onImageClick != null) {
                    int currentPosition = getBindingAdapterPosition();
                    if (currentPosition != RecyclerView.NO_POSITION) {
                        onImageClick.onClick(currentPosition, uri);
                    }
                }
            });

            imageView.setOnLongClickListener(v -> {
                if (itemTouchHelper != null) {
                    itemTouchHelper.startDrag(this);
                    return true;
                }
                return false;
            });

            dragSurface.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && itemTouchHelper != null) {
                    itemTouchHelper.startDrag(this);
                }
                return false;
            });
        }
    }
}
