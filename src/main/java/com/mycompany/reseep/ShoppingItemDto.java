package com.mycompany.reseep;

public class ShoppingItemDto {
    private Long id;
    private Long ingredientId;
    private String ingredientName;
    private String unit;
    private double qty;
    private String status;
    private String addedByUsername;
    private String boughtByUsername;
    private String boughtAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getIngredientId() { return ingredientId; }
    public void setIngredientId(Long ingredientId) { this.ingredientId = ingredientId; }
    public String getIngredientName() { return ingredientName; }
    public void setIngredientName(String ingredientName) { this.ingredientName = ingredientName; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public double getQty() { return qty; }
    public void setQty(double qty) { this.qty = qty; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAddedByUsername() { return addedByUsername; }
    public void setAddedByUsername(String addedByUsername) { this.addedByUsername = addedByUsername; }
    public String getBoughtByUsername() { return boughtByUsername; }
    public void setBoughtByUsername(String boughtByUsername) { this.boughtByUsername = boughtByUsername; }
    public String getBoughtAt() { return boughtAt; }
    public void setBoughtAt(String boughtAt) { this.boughtAt = boughtAt; }
}
