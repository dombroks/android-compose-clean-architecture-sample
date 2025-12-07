package test;

public enum MySingleton {
    INSTANCE;

    private String apiUrl;
    private int maxConnections;

    // You can initialize values in constructor
    MySingleton() {
        this.apiUrl = "https://example.com";
        this.maxConnections = 5;
    }

    // Getters & Setters
    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

}
