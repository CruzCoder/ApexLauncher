package net.kdt.pojavlaunch;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static net.kdt.pojavlaunch.prefs.LauncherPreferences.PREF_KEY_CURRENT_PROFILE;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;

import com.kdt.mcgui.ApexNavItem;
import com.kdt.mcgui.ProgressLayout;
import com.kdt.mcgui.mcAccountSpinner;
import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.extra.ExtraListener;
import net.kdt.pojavlaunch.fragments.MainMenuFragment;
import net.kdt.pojavlaunch.fragments.MicrosoftLoginFragment;
import net.kdt.pojavlaunch.fragments.ProfileTypeSelectFragment;
import net.kdt.pojavlaunch.fragments.SelectAuthFragment;
import net.kdt.pojavlaunch.lifecycle.ContextAwareDoneListener;
import net.kdt.pojavlaunch.lifecycle.ContextExecutor;
import net.kdt.pojavlaunch.modloaders.modpacks.ModloaderInstallTracker;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.IconCacheJanitor;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.profiles.ProfileIconCache;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;
import net.kdt.pojavlaunch.services.ProgressServiceKeeper;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.tasks.AsyncVersionList;
import net.kdt.pojavlaunch.tasks.MinecraftDownloader;
import net.kdt.pojavlaunch.utils.DateUtils;
import net.kdt.pojavlaunch.utils.NotificationUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Apex Launcher home screen.
 *
 * Landscape layout: navigation rail + hero instance card + information column.
 * Portrait layout: header + hero + information row + bottom navigation bar.
 * Both layouts share the same view ids, so this activity is layout agnostic.
 *
 * Actual fragments (settings, mod installers, mod store...) are displayed in
 * {@code container_fragment}, which is hidden while the home screen is showing.
 */
public class LauncherActivity extends BaseActivity {
    public static final String SETTING_FRAGMENT_TAG = "SETTINGS_FRAGMENT";

    public final ActivityResultLauncher<Object> modInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data)->{
                if(data != null) Tools.launchModInstaller(this, data);
            });

    private mcAccountSpinner mAccountSpinner;
    private mcVersionSpinner mVersionSpinner;
    private FragmentContainerView mFragmentView;
    private ProgressLayout mProgressLayout;
    private ProgressServiceKeeper mProgressServiceKeeper;
    private ModloaderInstallTracker mInstallTracker;
    private NotificationManager mNotificationManager;

    /* navigation */
    private ApexNavItem mNavHome;
    private ApexNavItem mNavLibrary;
    private ApexNavItem mNavMods;
    private ApexNavItem mNavControls;
    private ApexNavItem mNavSettings;

    /* hero */
    private TextView mInstanceTitle;
    private TextView mInstanceMeta;
    private TextView mMemoryView;
    private TextView mClockView;
    private ViewGroup mRecentContainer;

    private final SimpleDateFormat mClockFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final Runnable mClockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            Tools.MAIN_HANDLER.postDelayed(this, 30000L);
        }
    };

    /* Keeps the home chrome and the fragment container in sync with the back stack */
    private final FragmentManager.FragmentLifecycleCallbacks mFragmentCallbackListener =
            new FragmentManager.FragmentLifecycleCallbacks() {
        @Override
        public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
            boolean isHome = f instanceof MainMenuFragment;
            setHomeChromeVisible(isHome);
            if (isHome) refreshHome();
        }
    };

    /* Listener for the back button in settings */
    private final ExtraListener<String> mBackPreferenceListener = (key, value) -> {
        if(value.equals("true")) onBackPressed();
        return false;
    };

    /* Listener for the auth method selection screen */
    private final ExtraListener<Boolean> mSelectAuthMethod = (key, value) -> {
        Fragment fragment = getSupportFragmentManager().findFragmentById(mFragmentView.getId());
        // Allow starting the add account only from the main menu, should it be moved to fragment itself ?
        if(!(fragment instanceof MainMenuFragment)) return false;

        Tools.swapFragment(this, SelectAuthFragment.class, SelectAuthFragment.TAG, null);
        return false;
    };

    private final ExtraListener<Boolean> mLaunchGameListener = (key, value) -> {
        if(mProgressLayout.hasProcesses()){
            Toast.makeText(this, R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return false;
        }

        String selectedProfile = LauncherPreferences.DEFAULT_PREF.getString(PREF_KEY_CURRENT_PROFILE,"");
        if (LauncherProfiles.mainProfileJson == null || !LauncherProfiles.mainProfileJson.profiles.containsKey(selectedProfile)){
            Toast.makeText(this, R.string.error_no_version, Toast.LENGTH_LONG).show();
            return false;
        }
        MinecraftProfile prof = LauncherProfiles.mainProfileJson.profiles.get(selectedProfile);
        if (prof == null || prof.lastVersionId == null || "Unknown".equals(prof.lastVersionId)){
            Toast.makeText(this, R.string.error_no_version, Toast.LENGTH_LONG).show();
            return false;
        }

        if(mAccountSpinner.getSelectedAccount() == null){
            Toast.makeText(this, R.string.no_saved_accounts, Toast.LENGTH_LONG).show();
            ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true);
            return false;
        }
        String normalizedVersionId = AsyncMinecraftDownloader.normalizeVersionId(prof.lastVersionId);
        JMinecraftVersionList.Version mcVersion = AsyncMinecraftDownloader.getListedVersion(normalizedVersionId);

        // Do not load when is a modded version or older than minecraft 1.3 on demo account
        if (mAccountSpinner.getSelectedAccount().isDemo()) {
            boolean isOlderThan13 = true;

            if (mcVersion != null) {
                try {
                    isOlderThan13 = DateUtils.dateBefore(DateUtils.parseReleaseDate(mcVersion.releaseTime), 2012, 6, 22);
                } catch (ParseException ignored) {}
            }

            if (isOlderThan13) {
                Toast.makeText(this, R.string.toast_not_available_demo, Toast.LENGTH_LONG).show();
                return false;
            }
        }

        new MinecraftDownloader().start(
                this,
                mcVersion,
                normalizedVersionId,
                new ContextAwareDoneListener(this, normalizedVersionId)
        );
        return false;
    };

    private final TaskCountListener mDoubleLaunchPreventionListener = taskCount -> {
        // Hide the notification that starts the game if there are tasks executing.
        // Prevents the user from trying to launch the game with tasks ongoing.
        if(taskCount > 0) {
            Tools.runOnUiThread(() ->
                    mNotificationManager.cancel(NotificationUtils.NOTIFICATION_ID_GAME_START)
            );
        }
    };

    private ActivityResultLauncher<String> mRequestNotificationPermissionLauncher;
    private WeakReference<Runnable> mRequestNotificationPermissionRunnable;

    @Override
    protected boolean shouldIgnoreNotch() {
        return getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT;
    }

    @Override
    public boolean setFullscreen() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pojav_launcher);
        FragmentManager fragmentManager = getSupportFragmentManager();
        // If we don't have a back stack root yet...
        if(fragmentManager.getBackStackEntryCount() < 1) {
            // Manually add the first fragment to the backstack to get easily back to it
            // There must be a better way to handle the root though...
            // (artDev: No, there is not. I've spent days researching this for another unrelated project.)
            fragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .addToBackStack("ROOT")
                    .add(R.id.container_fragment, MainMenuFragment.class, null, "ROOT").commit();
        }


        IconCacheJanitor.runJanitor();
        mRequestNotificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isAllowed -> {
                    if(!isAllowed) handleNoNotificationPermission();
                    else {
                        Runnable runnable = Tools.getWeakReference(mRequestNotificationPermissionRunnable);
                        if(runnable != null) runnable.run();
                    }
                }
        );
        getWindow().setBackgroundDrawable(null);
        bindViews();
        bindNavigation();
        checkNotificationPermission();
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        ProgressKeeper.addTaskCountListener(mDoubleLaunchPreventionListener);
        ProgressKeeper.addTaskCountListener((mProgressServiceKeeper = new ProgressServiceKeeper(this)));

        ProgressKeeper.addTaskCountListener(mProgressLayout);
        ExtraCore.addExtraListener(ExtraConstants.BACK_PREFERENCE, mBackPreferenceListener);
        ExtraCore.addExtraListener(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);

        ExtraCore.addExtraListener(ExtraConstants.LAUNCH_GAME, mLaunchGameListener);

        new AsyncVersionList().getVersionList(versions -> ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, versions), false);

        mInstallTracker = new ModloaderInstallTracker(this);

        mProgressLayout.observe(ProgressLayout.DOWNLOAD_MINECRAFT);
        mProgressLayout.observe(ProgressLayout.UNPACK_RUNTIME);
        mProgressLayout.observe(ProgressLayout.INSTALL_MODPACK);
        mProgressLayout.observe(ProgressLayout.AUTHENTICATE_MICROSOFT);
        mProgressLayout.observe(ProgressLayout.DOWNLOAD_VERSION_LIST);

        refreshHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ContextExecutor.setActivity(this);
        mInstallTracker.attach();
        Tools.MAIN_HANDLER.removeCallbacks(mClockTick);
        Tools.MAIN_HANDLER.post(mClockTick);
        refreshHome();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ContextExecutor.clearActivity();
        mInstallTracker.detach();
        Tools.MAIN_HANDLER.removeCallbacks(mClockTick);
    }

    @Override
    protected void onStart() {
        super.onStart();
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(mFragmentCallbackListener, true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Tools.MAIN_HANDLER.removeCallbacks(mClockTick);
        mProgressLayout.cleanUpObservers();
        ProgressKeeper.removeTaskCountListener(mProgressLayout);
        ProgressKeeper.removeTaskCountListener(mProgressServiceKeeper);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.BACK_PREFERENCE, mBackPreferenceListener);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.LAUNCH_GAME, mLaunchGameListener);

        getSupportFragmentManager().unregisterFragmentLifecycleCallbacks(mFragmentCallbackListener);
    }

    /** Custom implementation to feel more natural when a backstack isn't present */
    @Override
    public void onBackPressed() {
        MicrosoftLoginFragment fragment = (MicrosoftLoginFragment) getVisibleFragment(MicrosoftLoginFragment.TAG);
        if(fragment != null){
            if(fragment.canGoBack()){
                fragment.goBack();
                return;
            }
        }

        // Check if we are at the root then
        if(getVisibleFragment("ROOT") != null){
            finish();
        }

        super.onBackPressed();
    }

    @Override
    public void onAttachedToWindow() {
        LauncherPreferences.computeNotchSize(this);
    }

    @SuppressWarnings("SameParameterValue")
    private Fragment getVisibleFragment(String tag){
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        if(fragment != null && fragment.isVisible()){
            return fragment;
        }
        return null;
    }

    @SuppressWarnings("unused")
    private Fragment getVisibleFragment(int id){
        Fragment fragment = getSupportFragmentManager().findFragmentById(id);
        if(fragment != null && fragment.isVisible()){
            return fragment;
        }
        return null;
    }

    private void checkNotificationPermission() {
        if(LauncherPreferences.PREF_SKIP_NOTIFICATION_PERMISSION_CHECK ||
                checkForNotificationPermission()) {
            return;
        }

        if(ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.POST_NOTIFICATIONS)) {
            showNotificationPermissionReasoning();
            return;
        }
        askForNotificationPermission(null);
    }

    private void showNotificationPermissionReasoning() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.notification_permission_dialog_title)
                .setMessage(R.string.notification_permission_dialog_text)
                .setPositiveButton(android.R.string.ok, (d, w) -> askForNotificationPermission(null))
                .setNegativeButton(android.R.string.cancel, (d, w)-> handleNoNotificationPermission())
                .show();
    }

    private void handleNoNotificationPermission() {
        LauncherPreferences.PREF_SKIP_NOTIFICATION_PERMISSION_CHECK = true;
        LauncherPreferences.DEFAULT_PREF.edit()
                .putBoolean(LauncherPreferences.PREF_KEY_SKIP_NOTIFICATION_CHECK, true)
                .apply();
        Toast.makeText(this, R.string.notification_permission_toast, Toast.LENGTH_LONG).show();
    }

    public boolean checkForNotificationPermission() {
        return Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_DENIED;
    }

    public void askForNotificationPermission(Runnable onSuccessRunnable) {
        if(Build.VERSION.SDK_INT < 33) return;
        if(onSuccessRunnable != null) {
            mRequestNotificationPermissionRunnable = new WeakReference<>(onSuccessRunnable);
        }
        mRequestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    /** Stuff all the view boilerplate here */
    private void bindViews(){
        mFragmentView = findViewById(R.id.container_fragment);
        mAccountSpinner = findViewById(R.id.account_spinner);
        mProgressLayout = findViewById(R.id.progress_layout);
        mVersionSpinner = findViewById(R.id.mc_version_spinner);
        mInstanceTitle = findViewById(R.id.apex_instance_title);
        mInstanceMeta = findViewById(R.id.apex_instance_meta);
        mMemoryView = findViewById(R.id.apex_memory);
        mClockView = findViewById(R.id.apex_clock);
        mRecentContainer = findViewById(R.id.apex_recent_container);
        mNavHome = findViewById(R.id.apex_nav_home);
        mNavLibrary = findViewById(R.id.apex_nav_library);
        mNavMods = findViewById(R.id.apex_nav_mods);
        mNavControls = findViewById(R.id.apex_nav_controls);
        mNavSettings = findViewById(R.id.apex_nav_settings);
    }

    /* =====================================================================
     * Home screen logic
     * ===================================================================== */

    /** Connects the navigation rail / bottom bar and the hero actions. */
    private void bindNavigation(){
        mNavHome.setOnClickListener(v -> Tools.backToMainMenu(this));

        // "Instances": opens the profile/version picker, anchored on the hero card
        View.OnClickListener versionPicker = v -> {
            Tools.backToMainMenu(this);
            mVersionSpinner.postDelayed(() -> {
                if (mVersionSpinner != null) mVersionSpinner.performClick();
            }, 200);
        };
        mNavLibrary.setOnClickListener(versionPicker);

        // "Discover": mod loaders and modpack browser
        mNavMods.setOnClickListener(v ->
                Tools.swapFragment(this, ProfileTypeSelectFragment.class, ProfileTypeSelectFragment.TAG, null));

        mNavControls.setOnClickListener(v ->
                startActivity(new Intent(this, CustomControlsActivity.class)));

        mNavSettings.setOnClickListener(v ->
                Tools.swapFragment(this, LauncherPreferenceFragment.class, SETTING_FRAGMENT_TAG, null));

        findViewById(R.id.change_version_button).setOnClickListener(versionPicker);
        findViewById(R.id.edit_profile_button).setOnClickListener(v -> {
            if (mVersionSpinner != null) mVersionSpinner.openProfileEditor(this);
        });
        findViewById(R.id.play_button).setOnClickListener(v ->
                ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));
        ImageButton moreButton = findViewById(R.id.apex_more_button);
        if (moreButton != null) moreButton.setOnClickListener(v -> showInstanceMenu());

        View viewAll = findViewById(R.id.apex_view_all);
        if (viewAll != null) viewAll.setOnClickListener(versionPicker);
    }

    /** Shows/hides the home decoration when a fragment takes over the screen. */
    private void setHomeChromeVisible(boolean visible){
        if (mFragmentView != null) mFragmentView.setVisibility(visible ? View.GONE : View.VISIBLE);
        updateNavSelection(visible ? mNavHome : null);
    }

    private void updateNavSelection(ApexNavItem selected){
        ApexNavItem[] items = new ApexNavItem[]{mNavHome, mNavLibrary, mNavMods, mNavControls, mNavSettings};
        for (ApexNavItem item : items) {
            if (item != null) item.setSelected(item == selected);
        }
    }

    /** Refresh the hero card, the session card and the recent list. */
    public void refreshHome(){
        if (mInstanceTitle == null) return;
        LauncherProfiles.load();

        String profileKey = LauncherPreferences.DEFAULT_PREF.getString(PREF_KEY_CURRENT_PROFILE, "");
        MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(profileKey);
        String versionId = profile == null || profile.lastVersionId == null
                ? getString(R.string.apex_no_instance) : profile.lastVersionId;

        String instanceName = versionId;
        if (profile != null && Tools.isValidString(profile.name) && !"New".equalsIgnoreCase(profile.name)) {
            instanceName = profile.name;
        }
        mInstanceTitle.setText(instanceName);

        String renderer = Tools.LOCAL_RENDERER != null ? Tools.LOCAL_RENDERER : LauncherPreferences.PREF_RENDERER;
        String meta = String.format(Locale.US, "%s · %s · %d GB",
                versionId, renderer, LauncherPreferences.PREF_RAM_ALLOCATION / 1024);
        mInstanceMeta.setText(meta);

        updateClock();
        updateMemory();
        populateRecentProfiles();
    }

    private void updateClock(){
        if (mClockView == null) return;
        mClockView.setText(mClockFormat.format(new Date()));
    }

    private void updateMemory(){
        if (mMemoryView == null) return;
        int freeMb = Tools.getFreeDeviceMemory(this);
        int totalMb = Tools.getTotalDeviceMemory(this);
        mMemoryView.setText(getString(R.string.apex_memory_usage,
                formatGigabytes(freeMb), formatGigabytes(totalMb),
                LauncherPreferences.PREF_RAM_ALLOCATION / 1024));
    }

    private static String formatGigabytes(int megabytes){
        return String.format(Locale.US, "%.1f GB", megabytes / 1024f);
    }

    /** Fills the "recently played" card with the most recently used profiles. */
    private void populateRecentProfiles(){
        if (mRecentContainer == null) return;
        mRecentContainer.removeAllViews();

        Map<String, MinecraftProfile> profiles = LauncherProfiles.mainProfileJson.profiles;
        List<Map.Entry<String, MinecraftProfile>> entries = new ArrayList<>(profiles.entrySet());
        Collections.sort(entries, (a, b) -> String.valueOf(b.getValue().lastUsed)
                .compareTo(String.valueOf(a.getValue().lastUsed)));

        int shown = 0;
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Map.Entry<String, MinecraftProfile> entry : entries) {
            if (shown >= 3) break;
            MinecraftProfile profile = entry.getValue();
            if (profile == null) continue;

            View row = inflater.inflate(R.layout.item_apex_recent, mRecentContainer, false);
            ImageView icon = row.findViewById(R.id.apex_recent_icon);
            TextView title = row.findViewById(R.id.apex_recent_title);
            TextView subtitle = row.findViewById(R.id.apex_recent_subtitle);
            ImageButton play = row.findViewById(R.id.apex_recent_play);

            String key = entry.getKey();
            String label = Tools.isValidString(profile.name) && !"New".equalsIgnoreCase(profile.name)
                    ? profile.name : String.valueOf(profile.lastVersionId);
            title.setText(label);
            subtitle.setText(profile.lastVersionId != null ? profile.lastVersionId : getString(R.string.apex_no_instance));
            icon.setImageDrawable(ProfileIconCache.fetchIcon(getResources(), key, profile.icon));

            row.setOnClickListener(v -> selectProfile(key));
            play.setOnClickListener(v -> {
                selectProfile(key);
                ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
            });

            mRecentContainer.addView(row);
            shown++;
        }

        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setText(R.string.apex_no_recent);
            empty.setTextColor(ContextCompat.getColor(this, R.color.apex_text_faint));
            empty.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimension(R.dimen.apex_text_body));
            mRecentContainer.addView(empty);
        }
    }

    /** Selects a launcher profile (instance) and refreshes everything around it. */
    private void selectProfile(String profileKey){
        LauncherPreferences.DEFAULT_PREF.edit().putString(PREF_KEY_CURRENT_PROFILE, profileKey).apply();
        if (mVersionSpinner != null) {
            mVersionSpinner.reloadProfiles();
            int index = mVersionSpinner.getProfileAdapter().resolveProfileIndex(profileKey);
            if (index >= 0) mVersionSpinner.setProfileSelection(index);
        }
        refreshHome();
    }

    /** Overflow menu of the hero card. */
    private void showInstanceMenu(){
        String[] items = new String[]{
                getString(R.string.main_install_jar_file),
                getString(R.string.mcl_button_open_directory),
                getString(R.string.main_share_logs),
                getString(R.string.mcl_option_customcontrol),
                getString(R.string.apex_menu_help),
                getString(R.string.mcl_button_discord),
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.apex_more)
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            if (ProgressKeeper.getTaskCount() == 0) Tools.installMod(this, false);
                            else Toast.makeText(this, R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
                            break;
                        case 1:
                            Tools.openPath(this, getCurrentProfileDirectory(), false);
                            break;
                        case 2:
                            Tools.shareLog(this);
                            break;
                        case 3:
                            startActivity(new Intent(this, CustomControlsActivity.class));
                            break;
                        case 4:
                            Tools.openURL(this, Tools.URL_HOME);
                            break;
                        case 5:
                            Tools.openURL(this, getString(R.string.discord_invite));
                            break;
                    }
                })
                .show();
    }

    private File getCurrentProfileDirectory(){
        String currentProfile = LauncherPreferences.DEFAULT_PREF.getString(PREF_KEY_CURRENT_PROFILE, null);
        if(!Tools.isValidString(currentProfile)) return new File(Tools.DIR_GAME_NEW);
        LauncherProfiles.load();
        MinecraftProfile profileObject = LauncherProfiles.mainProfileJson.profiles.get(currentProfile);
        if(profileObject == null) return new File(Tools.DIR_GAME_NEW);
        return Tools.getGameDirPath(profileObject);
    }
}
