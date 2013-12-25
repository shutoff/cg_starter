package ru.shutoff.cgstarter;

import android.content.Context;
import android.widget.ArrayAdapter;

import java.util.HashMap;
import java.util.List;

public class StableArrayAdapter extends ArrayAdapter<String> {

    final int INVALID_ID = -1;

    HashMap<String, Integer> mIdMap = new HashMap<String, Integer>();
    List<String> array;
    int next;

    public StableArrayAdapter(Context context, int textViewResourceId, List<String> objects) {
        super(context, textViewResourceId, objects);
        array = objects;
        for (int i = 0; i < objects.size(); ++i) {
            mIdMap.put(objects.get(i), ++next);
        }
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position > array.size())
            return INVALID_ID;
        if (position == array.size())
            return 0;
        String item = getItem(position);
        return mIdMap.get(item);
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    void removeItem(String object) {
        mIdMap.remove(object);
    }

    void addItem(String object) {
        mIdMap.put(object, ++next);
    }
}
