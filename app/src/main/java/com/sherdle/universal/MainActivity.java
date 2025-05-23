package com.sherdle.universal;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.sherdle.universal.drawer.MenuItemCallback;
import com.sherdle.universal.drawer.NavItem;
import com.sherdle.universal.drawer.SimpleMenu;
import com.sherdle.universal.drawer.TabAdapter;
import com.sherdle.universal.inherit.BackPressFragment;
import com.sherdle.universal.inherit.CollapseControllingFragment;
import com.sherdle.universal.inherit.ConfigurationChangeFragment;
import com.sherdle.universal.inherit.PermissionsFragment;
import com.sherdle.universal.providers.CustomIntent;
import com.sherdle.universal.providers.fav.ui.FavFragment;
import com.sherdle.universal.util.CustomScrollingViewBehavior;
import com.sherdle.universal.util.Helper;
import com.sherdle.universal.util.Log;
import com.sherdle.universal.util.PurchaseHelper;
import com.sherdle.universal.util.ThemeUtils;
import com.sherdle.universal.util.layout.CustomAppBarLayout;
import com.sherdle.universal.util.layout.DisableableViewPager;
import com.sherdle.universal.util.layout.PrivacyBottomSheet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This file is part of the Universal template
 * For license information, please check the LICENSE
 * file in the root of this project
 *
 * @author Sherdle
 * Copyright 2019
 */
public class MainActivity extends AppCompatActivity implements MenuItemCallback, ConfigParser.CallBack {

    private static final int PERMISSION_REQUESTCODE = 123;

    //Layout
    public Toolbar mToolbar;
    private TabLayout tabLayout;
    private DisableableViewPager viewPager;
    private NavigationView navigationView;
    public DrawerLayout drawer;
    private ActionBarDrawerToggle toggle;
    private BottomNavigationView bottomNavigation;

    //Adapters
    private TabAdapter adapter;
    private static SimpleMenu menu;

    //Keep track of the interstitials we show
    private int interstitialCount = -1;
    private InterstitialAd mInterstitialAd;

    //Data to pass to a fragment
    public static String FRAGMENT_DATA = "transaction_data";
    public static String FRAGMENT_CLASS = "transation_target";
    public static String FRAGMENT_PROVIDER = "transation_provider";

    //Permissions Queu
    List<NavItem> queueItem;
    int queueMenuItemId;

    //InstanceState (rotation)
    private Bundle savedInstanceState;
    private static final String STATE_MENU_INDEX = "MENUITEMINDEX";
    private static final String STATE_PAGER_INDEX = "VIEWPAGERPOSITION";
    private static final String STATE_ACTIONS = "ACTIONS";


    @Override
    public void configLoaded(boolean facedException) {
        if (facedException || menu.getFirstMenuItem() == null) {
            if (Helper.isOnlineShowDialog(MainActivity.this))
                Toast.makeText(this, R.string.invalid_configuration, Toast.LENGTH_LONG).show();
        } else {
            if (savedInstanceState == null) {
                menuItemClicked(menu.getFirstMenuItem(), 0, false);
            } else {
                ArrayList<NavItem> actions = (ArrayList<NavItem>) savedInstanceState.getSerializable(STATE_ACTIONS);
                int menuItemId = savedInstanceState.getInt(STATE_MENU_INDEX);
                int viewPagerPosition = savedInstanceState.getInt(STATE_PAGER_INDEX);

                menuItemClicked(actions, menuItemId, false);
                viewPager.setCurrentItem(viewPagerPosition);
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.savedInstanceState = savedInstanceState;
        ThemeUtils.setTheme(this);
        PurchaseHelper.getPurchaseHelper(this).onAppLaunch();

        //Load the appropriate layout
        if (useTabletMenu()) {
            setContentView(R.layout.activity_main_tablet);
            Helper.setStatusBarColor(MainActivity.this,
                    ThemeUtils.getPrimaryDarkColor(this));
        } else {
            setContentView(R.layout.activity_main);
        }

        mToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);

        getSupportActionBar().setDisplayShowHomeEnabled(!useTabletMenu());

        if (Config.HIDE_TOOLBAR) {
            getSupportActionBar().hide();
        }

        //Drawer
        if (!useTabletMenu()) {
            drawer = findViewById(R.id.drawer);
            toggle = new ActionBarDrawerToggle(
                    this, drawer, mToolbar, R.string.drawer_open, R.string.drawer_close);
            drawer.setDrawerListener(toggle);
            toggle.syncState();
        }

        //Layouts
        tabLayout = findViewById(R.id.tabs);
        viewPager = findViewById(R.id.viewpager);
        bottomNavigation = findViewById(R.id.bottom_navigation);

        //Menu items
        navigationView = findViewById(R.id.nav_view);

        menu = new SimpleMenu(navigationView.getMenu(), this);
        if (Config.USE_HARDCODED_CONFIG) {
            Config.configureMenu(menu, this);
        } else if (!Config.CONFIG_URL.isEmpty() && Config.CONFIG_URL.contains("http"))
            new ConfigParser(Config.CONFIG_URL, menu, this, this).execute();
        else
            new ConfigParser("config.json", menu, this, this).execute();

        tabLayout.setupWithViewPager(viewPager);

        if (!useTabletMenu()) {
            drawer.setStatusBarBackgroundColor(
                    ThemeUtils.getPrimaryDarkColor(this));
        }

        applyDrawerLocks();

        Helper.updateAndroidSecurityProvider(this);
        //PrivacyBottomSheet.showPrivacySheetIfNeeded(this);
        PrivacyBottomSheet.askForUMPIfNeeded(this);

        //Ads
        Helper.admobLoader(this, findViewById(R.id.adView));

        if (getResources().getString(R.string.admob_interstitial_id).length() > 0
                && Config.INTERSTITIAL_INTERVAL > 0
                && !SettingsFragment.getIsPurchased(this)) {
            loadInterstitial();
        }

        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            public void onPageScrollStateChanged(int state) {
            }

            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            public void onPageSelected(int position) {
                if (bottomNavigation.getMenu().findItem(position) != null) //TODO why would it be nul?
                    bottomNavigation.getMenu().findItem(position).setChecked(true);
                onTabBecomesActive(position);
            }
        });

    }

    @SuppressLint("NewApi")
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUESTCODE) {
            boolean allGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                //Retry to open the menu item
                menuItemClicked(queueItem, queueMenuItemId, false);
            } else {
                // Permission Denied
                Toast.makeText(MainActivity.this, getResources().getString(R.string.permissions_required), Toast.LENGTH_SHORT)
                        .show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void menuItemClicked(List<NavItem> actions, int menuItemIndex, boolean requiresPurchase) {
        // Checking the drawer should be open on start
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        boolean openOnStart = Config.DRAWER_OPEN_START || prefs.getBoolean("menuOpenOnStart", false);
        if (drawer != null) {
            boolean firstClick = (savedInstanceState == null && adapter == null);
            if (openOnStart && !useTabletMenu() && firstClick) {
                drawer.openDrawer(GravityCompat.START);
            } else {
                //Close the drawer
                drawer.closeDrawer(GravityCompat.START);
            }
        }

        //Check if the user is allowed to open item
        if (requiresPurchase && !isPurchased()) return; //isPurchased will handle this.
        if (!checkPermissionsHandleIfNeeded(actions, menuItemIndex))
            return; //checkPermissions will handle.

        if (isCustomIntent(actions)) return;

        //Uncheck all other items, check the current item
        for (MenuItem menuItem : menu.getMenuItems()) {
            menuItem.setChecked(menuItem.getItemId() == menuItemIndex);
        }

        //Load the new tab
        adapter = new TabAdapter(getSupportFragmentManager(), actions, this);
        viewPager.setAdapter(adapter);
        configureBottomNavigation(actions);

        //Show or hide the tab bar depending on if we need it
        if (actions.size() == 1) {
            bottomNavigation.setVisibility(View.GONE);
            tabLayout.setVisibility(View.GONE);

            viewPager.setPagingEnabled(false);
        } else {
            if (Config.BOTTOM_TABS)
                bottomNavigation.setVisibility(View.VISIBLE);
            else
                tabLayout.setVisibility(View.VISIBLE);

            viewPager.setPagingEnabled(true);
        }

        showInterstitial();
        onTabBecomesActive(0);
    }

    private void configureBottomNavigation(List<NavItem> actions) {
        if (!Config.BOTTOM_TABS) return;

        bottomNavigation.getMenu().clear();
        int i = 0;
        for (NavItem item : actions) {
            if (i == 5) {
                Toast.makeText(this,
                        "With BottomTabs, you can not shown more than 5 entries. Remove some tabs to hide this message.",
                        Toast.LENGTH_LONG).show();
                break;
            }
            bottomNavigation.getMenu().add(Menu.NONE, i, Menu.NONE, item.getText(this)).setIcon(item.getTabIcon());
            i++;
        }

        bottomNavigation.setOnNavigationItemSelectedListener(
                new BottomNavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                        viewPager.setCurrentItem(item.getItemId());
                        return false;
                    }
                });
    }

    private void onTabBecomesActive(int position) {
        Fragment fragment = adapter.getItem(position);

        //If fragment does not support collapse, if OS does not support collapse, or if disabled, disable collapsing toolbar
        if ((fragment instanceof CollapseControllingFragment
                && !((CollapseControllingFragment) fragment).supportsCollapse()) || !Config.HIDING_TOOLBAR
                ||
                (android.os.Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT)) {
            lockAppBar();
        } else {
            unlockAppBar();
        }

        dynamicElevationAppBar(((!(fragment instanceof CollapseControllingFragment)) || ((CollapseControllingFragment) fragment).dynamicToolbarElevation()) && ThemeUtils.lightToolbarThemeActive(this));

        ((CustomAppBarLayout) mToolbar.getParent()).setExpanded(true, true);

        if (position != 0)
            showInterstitial();
    }

    /**
     * Show an interstitial ad
     */
    public void showInterstitial() {
        if (interstitialCount == (Config.INTERSTITIAL_INTERVAL - 1)) {
            if (mInterstitialAd != null) {
                mInterstitialAd.show(this);
                mInterstitialAd.setFullScreenContentCallback(new FullScreenContentCallback(){
                    @Override
                    public void onAdDismissedFullScreenContent() {
                        // Called when fullscreen content is dismissed.
                        Log.d("TAG", "The ad was dismissed.");

                        //Load a new ad
                        loadInterstitial();
                    }

                    @Override
                    public void onAdFailedToShowFullScreenContent(AdError adError) {
                        // Called when fullscreen content failed to show.
                        Log.d("TAG", "The ad failed to show.");
                    }

                    @Override
                    public void onAdShowedFullScreenContent() {
                        // Called when fullscreen content is shown.
                        mInterstitialAd = null;
                        Log.d("TAG", "The ad was shown.");
                    }
                });
            }

            interstitialCount = 0;
        } else {
            interstitialCount++;
        }
    }

    private void loadInterstitial(){
        AdRequest adRequestInter = new AdRequest.Builder().build();
        InterstitialAdLoadCallback callback = new InterstitialAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                super.onAdLoaded(interstitialAd);
                mInterstitialAd = interstitialAd;
            }
        };
        InterstitialAd.load(this, getResources().getString(R.string.admob_interstitial_id), adRequestInter, callback);
    }

    /**
     * Checks if the item is/contains a custom intent, and if that the case it will handle it.
     *
     * @param items List of NavigationItems
     * @return True if the item is a custom intent, in that case
     */
    private boolean isCustomIntent(List<NavItem> items) {
        NavItem customIntentItem = null;
        for (NavItem item : items) {
            if (CustomIntent.class.isAssignableFrom(item.getFragment())) {
                customIntentItem = item;
            }
        }

        if (customIntentItem == null) return false;
        if (items.size() > 1)
            Log.e("INFO", "Custom Intent Item must be only child of menu item! Ignoring all other tabs");

        CustomIntent.performIntent(MainActivity.this, customIntentItem.getData());
        return true;
    }

    /**
     * If the item can be opened because it either has been purchased or does not require a purchase to show.
     *
     * @return true if the app is purchased. False if the app hasn't been purchased, or if iaps are disabled
     */
    private boolean isPurchased() {
        String license = getResources().getString(R.string.google_play_license);
        // if item does not require purchase, or app has purchased, or license is null/empty (app has no in app purchases)
        if (!SettingsFragment.getIsPurchased(this) && !license.equals("")) {
            String[] extra = new String[]{SettingsFragment.SHOW_DIALOG};
            HolderActivity.startActivity(this, SettingsFragment.class, "settings", extra);

            return false;
        }

        return true;
    }

    /**
     * Checks if the item can be opened because it has sufficient permissions.
     *
     * @param tabs The tabs to check
     * @return true if the item is safe to open
     */
    private boolean checkPermissionsHandleIfNeeded(List<NavItem> tabs, int menuItemId) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return true;

        List<String> allPermissions = new ArrayList<>();
        for (NavItem tab : tabs) {
            if (PermissionsFragment.class.isAssignableFrom(tab.getFragment())) {
                try {
                    for (String permission : ((PermissionsFragment) tab.getFragment().newInstance()).requiredPermissions()) {
                        if (!allPermissions.contains(permission))
                            allPermissions.add(permission);
                    }
                } catch (Exception e) {
                    //Don't really care
                }
            }
        }

        if (allPermissions.size() > 0) {
            boolean allGranted = true;
            for (String permission : allPermissions) {
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
                    allGranted = false;
            }

            if (!allGranted) {
                requestPermissions(allPermissions.toArray(new String[0]), PERMISSION_REQUESTCODE);
                queueItem = tabs;
                queueMenuItemId = menuItemId;
                return false;
            }

            return true;
        }

        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.settings_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.settings:
                HolderActivity.startActivity(this, SettingsFragment.class);
                return true;
            case R.id.favorites:
                HolderActivity.startActivity(this, FavFragment.class);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        Fragment activeFragment = null;
        if (adapter != null)
            activeFragment = adapter.getCurrentFragment();

        if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (activeFragment instanceof BackPressFragment) {
            boolean handled = ((BackPressFragment) activeFragment).handleBackPress();
            if (!handled) {
                super.onBackPressed();
            }
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        if (fragments != null)
            for (Fragment frag : fragments)
                if (frag != null)
                    frag.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (adapter != null && !(adapter.getCurrentFragment() instanceof ConfigurationChangeFragment)) {
            this.recreate();
        }
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (adapter == null) return;

        int menuItemIndex = 0;
        for (MenuItem menuItem : menu.getMenuItems()) {
            if (menuItem.isChecked()) {
                menuItemIndex = menuItem.getItemId();
                break;
            }
        }

        outState.putSerializable(STATE_ACTIONS, ((ArrayList<NavItem>) adapter.getActions()));
        outState.putInt(STATE_MENU_INDEX, menuItemIndex);
        outState.putInt(STATE_PAGER_INDEX, viewPager.getCurrentItem());
    }

    //Check if we should adjust our layouts for tablets
    public boolean useTabletMenu() {
        return (getResources().getBoolean(R.bool.isWideTablet) && Config.TABLET_LAYOUT);
    }

    //Apply the appropiate locks to the drawer
    public void applyDrawerLocks() {
        if (drawer == null) {
            if (Config.HIDE_DRAWER)
                navigationView.setVisibility(View.GONE);
            return;
        }

        if (Config.HIDE_DRAWER) {
            toggle.setDrawerIndicatorEnabled(false);
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        } else {
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        }
    }

    private void lockAppBar() {
        AppBarLayout.LayoutParams params =
                (AppBarLayout.LayoutParams) mToolbar.getLayoutParams();
        params.setScrollFlags(0);
    }

    private void unlockAppBar() {
        AppBarLayout.LayoutParams params =
                (AppBarLayout.LayoutParams) mToolbar.getLayoutParams();
        params.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL
                | AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS);
    }

    private void dynamicElevationAppBar(boolean enabled){
        CoordinatorLayout.LayoutParams params =
                (CoordinatorLayout.LayoutParams) ((RelativeLayout) viewPager.getParent()).getLayoutParams();
        ((CustomScrollingViewBehavior) params.getBehavior()).setDynamicElevation(enabled);
        mToolbar.requestLayout();
    }
}