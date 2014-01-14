package ru.shutoff.cgstarter;

import android.location.Location;

import java.io.Serializable;
import java.util.Vector;

public abstract class SearchRequest {

    abstract Location getLocation();

    abstract void showError(String error);

    abstract void result(Vector<Address> result);

    static class Address implements Serializable {
        String address;
        String name;
        double lat;
        double lon;
        double distance;
        float scope;
    }

    void search(final String query) {
        PlaceRequest request = new PlaceRequest() {
            @Override
            Location getLocation() {
                return SearchRequest.this.getLocation();
            }

            @Override
            void showError(String error) {
                SearchRequest.this.showError(error);
            }

            @Override
            void result(Vector<Address> result) {
                if (result.size() > 0) {
                    SearchRequest.this.result(result);
                    return;
                }
                PlaceRequest req = new PlaceRequest() {
                    @Override
                    Location getLocation() {
                        return SearchRequest.this.getLocation();
                    }

                    @Override
                    void showError(String error) {
                        SearchRequest.this.showError(error);
                    }

                    @Override
                    void result(Vector<Address> result) {
                        if (result.size() > 0) {
                            SearchRequest.this.result(result);
                            return;
                        }
                        LocationRequest req = new LocationRequest() {
                            @Override
                            Location getLocation() {
                                return SearchRequest.this.getLocation();
                            }

                            @Override
                            void showError(String error) {
                                SearchRequest.this.showError(error);
                            }

                            @Override
                            void result(Vector<Address> result) {
                                SearchRequest.this.result(result);
                            }
                        };
                        req.execute(query);
                    }
                };
                req.execute(query, "50000");
            }
        };
        request.execute(query, "1000");
    }

/*

*/
}
