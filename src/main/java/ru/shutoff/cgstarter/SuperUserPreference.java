package ru.shutoff.cgstarter;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.preference.CheckBoxPreference;
import android.util.AttributeSet;
import android.view.View;

import java.io.DataOutputStream;

public class SuperUserPreference extends CheckBoxPreference {

    public SuperUserPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onClick() {
        if (!isChecked()) {
            Context context = getContext();
            final AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle(context.getString(R.string.require_su))
                    .setMessage(context.getString(R.string.require_su_msg))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok, null)
                    .create();
            dialog.show();
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (doRoot(""))
                        toggleValue();
                    dialog.dismiss();
                }
            });
            return;
        }
        super.onClick();
    }

    void toggleValue() {
        super.onClick();
    }

    static boolean doRoot(String command) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(command);
            os.writeBytes("\nexit\n");
            os.flush();
            p.waitFor();
            int ev = p.exitValue();
            if (ev == 0)
                return true;
        } catch (Exception ex) {
            // ignore
        }
        return false;
    }

}
