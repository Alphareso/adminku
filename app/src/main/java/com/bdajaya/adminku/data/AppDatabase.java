package com.bdajaya.adminku.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.bdajaya.adminku.data.dao.*;
import com.bdajaya.adminku.data.entity.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.bdajaya.adminku.db.migration.Migrations.MIGRATION_5_6;

@Database(entities = {
        Product.class,
        ProductImage.class,
        Category.class,
        Unit.class,
        StockTransaction.class,
        Brand.class,
        ProductFts.class
}, version = 6, exportSchema = false)
@TypeConverters({DateConverter.class, StringListConverter.class})
public abstract class AppDatabase extends RoomDatabase  {
    private static final String DATABASE_NAME = "adminku_db";
    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;

    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public abstract ProductDao productDao();
    public abstract ProductImageDao productImageDao();
    public abstract CategoryDao categoryDao();
    public abstract UnitDao unitDao();
    public abstract StockTransactionDao stockTransactionDao();
    public abstract BrandDao brandDao();
    public abstract ProductFtsDao productFtsDao();

    public static AppDatabase getInstance(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    DATABASE_NAME)
                            .addCallback(new Callback() {
                                @Override
                                public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                    super.onCreate(db);
                                    databaseWriteExecutor.execute(() -> {
                                        // Populate the database with initial data
                                        DatabaseInitializer.populateDatabase(getInstance(context));
                                    });
                                }
                            })
                            .addMigrations(MIGRATION_5_6)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
