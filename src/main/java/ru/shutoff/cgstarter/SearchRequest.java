package ru.shutoff.cgstarter;

import android.location.Location;

import java.util.Vector;

public abstract class SearchRequest {

    String error;

    abstract Location getLocation();

    abstract void showError(String error);

    abstract void result(Vector<Address> result);

    static class Address {
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
                State.appendLog("error...");
                SearchRequest.this.showError(error);
            }

            @Override
            void result(Vector<Address> result) {
                State.appendLog("result " + result.size());
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
                        State.appendLog("result " + result.size());
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
                                State.appendLog("res " + result.size());
                                SearchRequest.this.result(result);
                            }
                        };
                        req.execute(query);
                        State.appendLog("Execute location request " + query);
                    }
                };
                State.appendLog("execute request " + query);
                req.execute(query, "50000");
            }
        };
        State.appendLog("execute near request " + query);
        request.execute(query, "1000");
    }

/*

*/
}
