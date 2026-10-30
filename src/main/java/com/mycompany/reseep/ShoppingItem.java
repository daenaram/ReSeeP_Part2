package com.mycompany.reseep;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * One line on the shared shopping list: an ingredient, how much is needed,
 * who added it (from planning a recipe), and if/when/by whom it was bought.
 */
@Entity
@Table(name = "shopping_item")
public class ShoppingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Column(nullable = false)
    private double qty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShoppingStatus status = ShoppingStatus.NEEDED;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "added_by_id", nullable = false)
    private AppUser addedBy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bought_by_id")
    private AppUser boughtBy;

    @Column(name = "bought_at")
    private LocalDateTime boughtAt;

    public ShoppingItem() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Ingredient getIngredient() { return ingredient; }
    public void setIngredient(Ingredient ingredient) { this.ingredient = ingredient; }
    public double getQty() { return qty; }
    public void setQty(double qty) { this.qty = qty; }
    public ShoppingStatus getStatus() { return status; }
    public void setStatus(ShoppingStatus status) { this.status = status; }
    public AppUser getAddedBy() { return addedBy; }
    public void setAddedBy(AppUser addedBy) { this.addedBy = addedBy; }
    public AppUser getBoughtBy() { return boughtBy; }
    public void setBoughtBy(AppUser boughtBy) { this.boughtBy = boughtBy; }
    public LocalDateTime getBoughtAt() { return boughtAt; }
    public void setBoughtAt(LocalDateTime boughtAt) { this.boughtAt = boughtAt; }
}
