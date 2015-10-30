package ru.shutoff.cgstarter;

import org.json.JSONException;
import org.json.JSONObject;

public abstract class AddressRequest extends HttpTask {

    abstract void address(String s);

    void execute(double lat, double lon) {
        String url = "http://nominatim.openstreetmap.org/reverse?lat=$1&lon=$2&osm_type=N&format=json&address_details=0&accept-language=";
        super.execute(url, lat, lon);
    }

    @Override
    void result(JSONObject res) throws JSONException {
        address(res.getString("display_name"));
    }


    @Override
    void error(String err) {
        address(null);
    }
}