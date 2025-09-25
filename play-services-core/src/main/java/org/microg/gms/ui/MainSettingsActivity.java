package org.microg.gms.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.gms.R;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.microg.gms.ui.settings.SettingsProvider;

import java.util.Objects;

import static org.microg.gms.ui.settings.SettingsProviderKt.getAllSettingsProviders;

public class MainSettingsActivity extends AppCompatActivity {
    private AppBarConfiguration appBarConfiguration;

    private NavController getNavController() {
        return ((NavHostFragment) Objects.requireNonNull(getSupportFragmentManager().findFragmentById(R.id.navhost))).getNavController();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableEdgeToEdgeNoContrast();

        Intent intent = getIntent();
        for (SettingsProvider settingsProvider : getAllSettingsProviders(this)) {
            settingsProvider.preProcessSettingsIntent(intent);
        }

        setContentView(R.layout.settings_root_activity);

        Toolbar toolbar = findViewById(R.id.toolbar);
        CollapsingToolbarLayout toolbarLayout = findViewById(R.id.collapsing_toolbar);
        setSupportActionBar(toolbar);

        View rootLayout = findViewById(R.id.root_layout);
        ExtendedFloatingActionButton fab = findViewById(R.id.preference_fab);
        NestedScrollView nestedScrollView = findViewById(R.id.nested_scroll_view);

        final int initialScrollViewPaddingLeft = nestedScrollView.getPaddingLeft();
        final int initialScrollViewPaddingTop = nestedScrollView.getPaddingTop();
        final int initialScrollViewPaddingRight = nestedScrollView.getPaddingRight();
        final int initialScrollViewPaddingBottom = nestedScrollView.getPaddingBottom();

        ViewGroup.MarginLayoutParams fabInitialParams = (ViewGroup.MarginLayoutParams) fab.getLayoutParams();
        final int initialFabMarginLeft = fabInitialParams.leftMargin;
        final int initialFabMarginRight = fabInitialParams.rightMargin;
        final int initialFabMarginBottom = fabInitialParams.bottomMargin;

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
            Insets systemBarsInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());

            Insets imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime());

            boolean imeVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime());
            int bottomInset = imeVisible ? imeInsets.bottom : systemBarsInsets.bottom;

            nestedScrollView.setPadding(initialScrollViewPaddingLeft + systemBarsInsets.left, initialScrollViewPaddingTop, initialScrollViewPaddingRight + systemBarsInsets.right, initialScrollViewPaddingBottom + bottomInset);

            ViewGroup.MarginLayoutParams fabParams = (ViewGroup.MarginLayoutParams) fab.getLayoutParams();
            fabParams.leftMargin = initialFabMarginLeft + systemBarsInsets.left;
            fabParams.rightMargin = initialFabMarginRight + systemBarsInsets.right;
            fabParams.bottomMargin = initialFabMarginBottom + systemBarsInsets.bottom;
            fab.setLayoutParams(fabParams);

            return windowInsets;
        });

        for (SettingsProvider settingsProvider : getAllSettingsProviders(this)) {
            settingsProvider.extendNavigation(getNavController());
        }

        appBarConfiguration = new AppBarConfiguration.Builder(getNavController().getGraph()).build();
        NavigationUI.setupWithNavController(toolbarLayout, toolbar, getNavController(), appBarConfiguration);

        nestedScrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollY > oldScrollY) {
                fab.shrink();
            } else {
                fab.extend();
            }
        });
    }

    private void enableEdgeToEdgeNoContrast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            EdgeToEdge.enable(this, SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT));
            getWindow().setNavigationBarContrastEnforced(false);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(getNavController(), appBarConfiguration) || super.onSupportNavigateUp();
    }
}