package ru.shutoff.cgstarter;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

public class EditActivity extends Activity {

    static final String EXTRA_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE";
    static final String EXTRA_STRING_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB";

    static final double RUN_CG = -300.;
    static final double CLEAR_CG = -301;

    Bookmarks.Point[] poi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bookmarks.Point[] p = Bookmarks.get(this);
        poi = new Bookmarks.Point[p.length + 2];
        poi[0] = new Bookmarks.Point();
        poi[0].name = getString(R.string.cg_start);
        poi[0].lat = RUN_CG;
        poi[0].lng = RUN_CG;
        poi[1] = new Bookmarks.Point();
        poi[1].name = getString(R.string.no_route);
        poi[1].lat = CLEAR_CG;
        poi[1].lng = CLEAR_CG;
        System.arraycopy(p, 0, poi, 2, p.length);

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.item)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.cancel, null)
                .setView(inflater.inflate(R.layout.points, null))
                .create();
        dialog.show();
        final Spinner spPoints = (Spinner) dialog.findViewById(R.id.points);
        spPoints.setAdapter(new PointsAdapter());
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                finish();
            }
        });
        final Button btnOk = dialog.getButton(Dialog.BUTTON_POSITIVE);
        btnOk.setEnabled(false);
        btnOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = getIntent();
                Bookmarks.Point p = poi[spPoints.getSelectedItemPosition()];
                Bundle bundle = new Bundle();
                intent.putExtra(EXTRA_STRING_BLURB, p.name);
                if ((p.lat == RUN_CG) && (p.lng == RUN_CG)) {
                    bundle.putString(State.ROUTE, "");
                } else if ((p.lat == CLEAR_CG) && (p.lng == CLEAR_CG)) {
                    bundle.putString(State.ROUTE, "-");
                } else {
                    bundle.putString(State.ROUTE, p.lat + "|" + p.lng);
                }
                bundle.putString(State.POINTS, (p.points == null) ? "" : p.points);
                intent.putExtra(EXTRA_BUNDLE, bundle);
                setResult(RESULT_OK, intent);
                dialog.dismiss();
            }
        });
        spPoints.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                btnOk.setEnabled(true);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                btnOk.setEnabled(false);
            }
        });
        String name = getIntent().getStringExtra(EXTRA_STRING_BLURB);
        if (name != null) {
            for (int i = 0; i < poi.length; i++) {
                if (poi[i].name.equals(name)) {
                    spPoints.setSelection(i);
                    break;
                }
            }
        }
    }

    class PointsAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return poi.length;
        }

        @Override
        public Object getItem(int position) {
            return poi[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                LayoutInflater inflater = (LayoutInflater) getBaseContext()
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = inflater.inflate(R.layout.point_item, null);
            }
            TextView tvName = (TextView) v.findViewById(R.id.name);
            tvName.setText(poi[position].name);
            return v;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                LayoutInflater inflater = (LayoutInflater) getBaseContext()
                        .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = inflater.inflate(R.layout.point_dropdown, null);
            }
            TextView tvName = (TextView) v.findViewById(R.id.name);
            tvName.setText(poi[position].name);
            return v;

        }
    }

}
