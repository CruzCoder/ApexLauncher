package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;

/**
 * Root fragment of the launcher back stack.
 *
 * The Apex home screen itself is drawn by {@link LauncherActivity}
 * (layout {@code activity_pojav_launcher}), so this fragment is only the
 * anchor used by {@code Tools.backToMainMenu()} and by the back button.
 * It asks the activity to refresh the home content every time it comes back
 * to the foreground (e.g. after installing a mod loader).
 */
public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    public MainMenuFragment() {
        super(R.layout.fragment_apex_home_placeholder);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof LauncherActivity) {
            ((LauncherActivity) getActivity()).refreshHome();
        }
    }
}
