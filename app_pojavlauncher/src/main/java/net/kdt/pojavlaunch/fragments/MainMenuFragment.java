package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.BuildConfig;
import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.profiles.ProfileIconCache;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.util.Locale;

/**
 * The launcher dashboard, "Your space".
 * Left : icon rail. Center : quick cards + active world. Right : player card.
 * Every control of the old menu is still reachable, it just moved into the new layout.
 */
public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;
    private ScrollView mDashboardScroll;
    private TextView mBuildText;
    private TextView mPlayerVersionText;
    private TextView mPlayerBuildText;
    private ImageView mPlayerAvatar;
    private TextView mBrandText;

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Icon rail
        View newsButton = view.findViewById(R.id.news_button);
        View exploreButton = view.findViewById(R.id.explore_button);
        View installJarButton = view.findViewById(R.id.install_jar_button);
        View customControlButton = view.findViewById(R.id.custom_control_button);
        View shareLogsButton = view.findViewById(R.id.share_logs_button);
        View openDirectoryButton = view.findViewById(R.id.open_files_button);
        View homeButton = view.findViewById(R.id.rail_home);

        // Quick cards
        View socialsCard = view.findViewById(R.id.card_socials);
        View exploreCard = view.findViewById(R.id.card_explore);
        View youCard = view.findViewById(R.id.card_you);

        // Active world + player card
        View editProfileButton = view.findViewById(R.id.edit_profile_button);
        Button playButton = view.findViewById(R.id.play_button);
        Button sidePlayButton = view.findViewById(R.id.play_button_side);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);
        mDashboardScroll = view.findViewById(R.id.dashboard_scroll);
        mBuildText = view.findViewById(R.id.dashboard_build_text);
        mPlayerVersionText = view.findViewById(R.id.dashboard_player_version);
        mPlayerBuildText = view.findViewById(R.id.dashboard_player_build);
        mPlayerAvatar = view.findViewById(R.id.dashboard_avatar);
        mBrandText = view.findViewById(R.id.dashboard_brand);

        // --- Navigation -----------------------------------------------------
        homeButton.setOnClickListener(v -> {
            if(mDashboardScroll != null) mDashboardScroll.smoothScrollTo(0, 0);
            mVersionSpinner.reloadProfiles();
        });
        newsButton.setOnClickListener(v -> Tools.openURL(requireActivity(), Tools.URL_HOME));
        newsButton.setOnLongClickListener(v -> {
            Tools.swapFragment(requireActivity(), GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
            return true;
        });
        exploreButton.setOnClickListener(v -> openModSearch());
        socialsCard.setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.discord_invite)));
        exploreCard.setOnClickListener(v -> openModSearch());
        youCard.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));
        editProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));

        customControlButton.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        installJarButton.setOnClickListener(v -> runInstallerWithConfirmation(false));
        installJarButton.setOnLongClickListener(v -> {
            runInstallerWithConfirmation(true);
            return true;
        });
        shareLogsButton.setOnClickListener(v -> shareLog(requireContext()));
        openDirectoryButton.setOnClickListener(this::onOpenDirectoryClicked);

        playButton.setOnClickListener(v -> launchGame());
        sidePlayButton.setOnClickListener(v -> launchGame());

        if(mBrandText != null)
            mBrandText.setText(getString(R.string.dashboard_brand_version, BuildConfig.VERSION_NAME));

        updateDashboard();
    }

    private void openModSearch(){
        Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG, null);
    }

    private void launchGame(){
        ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
    }

    private void onOpenDirectoryClicked(View v){
        Tools.switchDemo(Tools.isDemoProfile(v.getContext())); // avoid switching accounts being able to access
        if(Tools.isDemoProfile(v.getContext())){
            Toast.makeText(v.getContext(), R.string.toast_not_available_demo, Toast.LENGTH_LONG).show();
            return;
        }

        openPath(v.getContext(), getCurrentProfileDirectory(), false);
    }

    /** Refresh every piece of text that depends on the selected profile. */
    private void updateDashboard(){
        MinecraftProfile profile = getCurrentProfile();
        String version = profile != null && Tools.isValidString(profile.lastVersionId)
                ? profile.lastVersionId
                : getString(R.string.dashboard_unknown_version);
        String loader = detectLoader(profile == null ? null : profile.lastVersionId);
        String profileKey = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");

        if(mBuildText != null) {
            mBuildText.setText(getString(R.string.dashboard_build_format, loader, version));
        }
        if(mPlayerVersionText != null) mPlayerVersionText.setText(version);
        if(mPlayerBuildText != null) {
            mPlayerBuildText.setText(getString(R.string.dashboard_minecraft_format, version));
        }
        if(mPlayerAvatar != null && Tools.isValidString(profileKey)) {
            mPlayerAvatar.setImageDrawable(ProfileIconCache.fetchIcon(
                    getResources(), profileKey, profile == null ? null : profile.icon));
        }
    }

    private MinecraftProfile getCurrentProfile(){
        String profileKey = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        if(!Tools.isValidString(profileKey)) return null;
        LauncherProfiles.load();
        if(LauncherProfiles.mainProfileJson == null) return null;
        return LauncherProfiles.mainProfileJson.profiles.get(profileKey);
    }

    /** Guess the mod loader from the version id, purely cosmetic. */
    private String detectLoader(@Nullable String versionId){
        if(versionId == null) return getString(R.string.dashboard_loader_vanilla);
        String lowerCaseId = versionId.toLowerCase(Locale.ROOT);
        if(lowerCaseId.contains("neoforge")) return getString(R.string.dashboard_loader_neoforge);
        if(lowerCaseId.contains("forge")) return getString(R.string.dashboard_loader_forge);
        if(lowerCaseId.contains("quilt")) return getString(R.string.dashboard_loader_quilt);
        if(lowerCaseId.contains("fabric")) return getString(R.string.dashboard_loader_fabric);
        if(lowerCaseId.contains("optifine")) return getString(R.string.dashboard_loader_optifine);
        return getString(R.string.dashboard_loader_vanilla);
    }

    private File getCurrentProfileDirectory() {
        String currentProfile = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        if(!Tools.isValidString(currentProfile)) return new File(Tools.DIR_GAME_NEW);
        LauncherProfiles.load();
        MinecraftProfile profileObject = LauncherProfiles.mainProfileJson.profiles.get(currentProfile);
        if(profileObject == null) return new File(Tools.DIR_GAME_NEW);
        return Tools.getGameDirPath(profileObject);
    }

    @Override
    public void onResume() {
        super.onResume();
        mVersionSpinner.reloadProfiles();
        updateDashboard();
    }

    private void runInstallerWithConfirmation(boolean isCustomArgs) {
        // avoid using custom installers to install a version
        if(Tools.isLocalProfile(requireContext()) || Tools.isDemoProfile(requireContext())){
            Toast.makeText(requireContext(), R.string.toast_not_available_demo, Toast.LENGTH_LONG).show();
            return;
        }

        if (ProgressKeeper.getTaskCount() == 0)
            Tools.installMod(requireActivity(), isCustomArgs);
        else
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }
}
