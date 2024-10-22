package com.codename1.contacts;

public class Address {

    private String streetAddress;
    private String locality;
    private String region;
    private String postalCode;
    private String country;

    public Address() {
    }

    public String getCountry() {
        return country;
    }

    public String getLocality() {
        return locality;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getRegion() {
        return region;
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public void setLocality(String locality) {
        this.locality = locality;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public void setStreetAddress(String streetAddress) {
        this.streetAddress = streetAddress;
    }

    
}
