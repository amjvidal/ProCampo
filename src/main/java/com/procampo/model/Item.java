package com.procampo.model;

import java.util.Objects;

/**
 * Entidade Item - Representa a fonte única de preços no catálogo agropecuário.
 * Atributos principais: ID_Item, Descricao, Unidade_Medida, Valor_Unitario.
 */
public class Item {
    private int idItem;
    private String descricao;
    private String unidadeMedida;
    private double valorUnitario;
    private String categoria;

    public Item() {
    }

    public Item(int idItem, String descricao, String unidadeMedida, double valorUnitario, String categoria) {
        this.idItem = idItem;
        this.descricao = descricao;
        this.unidadeMedida = unidadeMedida;
        this.valorUnitario = valorUnitario;
        this.categoria = categoria;
    }

    public Item(int idItem, String descricao, String unidadeMedida, double valorUnitario) {
        this(idItem, descricao, unidadeMedida, valorUnitario, "");
    }

    public Item(String descricao, String unidadeMedida, double valorUnitario, String categoria) {
        this(0, descricao, unidadeMedida, valorUnitario, categoria);
    }

    public Item(String descricao, String unidadeMedida, double valorUnitario) {
        this(0, descricao, unidadeMedida, valorUnitario, "");
    }

    public int getIdItem() {
        return idItem;
    }

    public void setIdItem(int idItem) {
        this.idItem = idItem;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getUnidadeMedida() {
        return unidadeMedida;
    }

    public void setUnidadeMedida(String unidadeMedida) {
        this.unidadeMedida = unidadeMedida;
    }

    public double getValorUnitario() {
        return valorUnitario;
    }

    public void setValorUnitario(double valorUnitario) {
        this.valorUnitario = valorUnitario;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Item item = (Item) o;
        return idItem == item.idItem &&
                Double.compare(item.valorUnitario, valorUnitario) == 0 &&
                Objects.equals(descricao, item.descricao) &&
                Objects.equals(unidadeMedida, item.unidadeMedida) &&
                Objects.equals(categoria, item.categoria);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idItem, descricao, unidadeMedida, valorUnitario, categoria);
    }

    @Override
    public String toString() {
        return descricao + " (" + unidadeMedida + ") " + (categoria != null && !categoria.isEmpty() ? "[" + categoria + "] " : "") + "- R$ " + String.format("%.2f", valorUnitario);
    }
}
