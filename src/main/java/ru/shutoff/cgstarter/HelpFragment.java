package ru.shutoff.cgstarter;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class HelpFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.help, container, false);

        try {
            PackageManager pkgManager = getActivity().getPackageManager();
            PackageInfo info = pkgManager.getPackageInfo("ru.shutoff.cgstarter", 0);
            TextView tvVersion = (TextView) v.findViewById(R.id.version);
            tvVersion.setText(getString(R.string.version) + " " + info.versionName);
        } catch (Exception ex) {
            // ignore
        }
        return v;
    }
}
