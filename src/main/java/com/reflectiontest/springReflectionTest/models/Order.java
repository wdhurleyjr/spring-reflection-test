package com.reflectiontest.springReflectionTest.models;

import java.util.List;

public class Order {
    private int orderId;
    private List<Product> products;
    private double total;

    public Order() {}

    public Order(int orderId, double total) {
        this.orderId = orderId;
        this.total = total;
    }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public List<Product> getProducts() { return products; }
    public void setProducts(List<Product> products) { this.products = products; }

    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }
}

