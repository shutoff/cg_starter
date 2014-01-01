package ru.shutoff.cgstarter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.preference.CheckBoxPreference;
import android.util.AttributeSet;
import android.widget.Toast;

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
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (doRoot(getContext(), ""))
                                toggleValue();
                            dialog.dismiss();
                        }
                    })
                    .create();
            dialog.show();
            return;
        }
        super.onClick();
    }

    void toggleValue() {
        super.onClick();
    }

    static boolean doRoot(Context context, String command) {
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
            Toast toast = Toast.makeText(context, ex.toString(), Toast.LENGTH_LONG);
            State.appendLog("su error " + command + " - " + ex.toString());
            // ignore
        }
        return false;
    }

}
