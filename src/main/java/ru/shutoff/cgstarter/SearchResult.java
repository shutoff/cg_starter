package ru.shutoff.cgstarter;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.text.DecimalFormat;
import java.util.Vector;

public class SearchResult extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            try {
                byte[] data = getIntent().getByteArrayExtra(State.INFO);
                ByteArrayInputStream bis = new ByteArrayInputStream(data);
                ObjectInput in = new ObjectInputStream(bis);
                fragment.addr_list = (Vector<SearchRequest.Address>) in.readObject();
                in.close();
                bis.close();
            } catch (Exception ex) {
            }
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, fragment)
                    .commit();
        }

        setResult(RESULT_CANCELED);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {

        Vector<SearchRequest.Address> addr_list;

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(final LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.list, container, false);

            if (savedInstanceState != null) {
                try {
                    byte[] data = savedInstanceState.getByteArray(State.INFO);
                    ByteArrayInputStream bis = new ByteArrayInputStream(data);
                    ObjectInput in = new ObjectInputStream(bis);
                    addr_list = (Vector<SearchRequest.Address>) in.readObject();
                    in.close();
                    bis.close();
                } catch (Exception ex) {
                }
            }

            rootView.findViewById(R.id.progress).setVisibility(View.GONE);
            ListView lv = (ListView) rootView.findViewById(R.id.list);
            lv.setAdapter(new BaseAdapter() {
                @Override
                public int getCount() {
                    return addr_list.size();
                }

                @Override
                public Object getItem(int position) {
                    return addr_list.get(position);
                }

                @Override
                public long getItemId(int position) {
                    return position;
                }

                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View v = convertView;
                    if (v == null)
                        v = inflater.inflate(R.layout.addr_item, null);
                    SearchRequest.Address addr = addr_list.get(position);
                    TextView tv = (TextView) v.findViewById(R.id.addr);
                    tv.setText(addr.address);
                    tv = (TextView) v.findViewById(R.id.name);
                    tv.setText(addr.name);
                    tv = (TextView) v.findViewById(R.id.dist);
                    if (addr.distance < 100) {
                        tv.setText("");
                    } else {
                        DecimalFormat df = new DecimalFormat("#.#");
                        tv.setText(df.format(addr.distance / 1000) + getString(R.string.km));
                    }
                    return v;
                }
            });
            lv.setVisibility(View.VISIBLE);
            lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    SearchRequest.Address addr = addr_list.get(i);
                    if (OnExitService.isRunCG(getActivity()))
                        CarMonitor.killCG(getActivity());
                    CarMonitor.startCG(getActivity(), addr.lat + "|" + addr.lon, null);
                    getActivity().setResult(RESULT_OK);
                    getActivity().finish();
                }
            });

            return rootView;
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            byte[] data = null;
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutput out = new ObjectOutputStream(bos);
                out.writeObject(addr_list);
                data = bos.toByteArray();
                out.close();
                bos.close();
            } catch (Exception ex) {
                // ignore
            }
            outState.putByteArray(State.INFO, data);
        }
    }
}
