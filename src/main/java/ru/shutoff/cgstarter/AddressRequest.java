package ru.shutoff.cgstarter;

import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.ParseException;

public abstract class AddressRequest extends HttpTask {

    abstract void address(String s);

    void execute(double lat, double lon) {
        String url = "http://nominatim.openstreetmap.org/reverse?lat=$1&lon=$2&osm_type=N&format=json&address_details=0&accept-language=";
        super.execute(url, lat, lon);
    }

    @Override
    void result(JsonObject res) throws ParseException {
        address(res.get("display_name").asString());
    }


    @Override
    void error(String err) {
        address(null);
    }
}