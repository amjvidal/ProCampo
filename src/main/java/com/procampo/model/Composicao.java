package com.procampo.model;

import java.util.Objects;

/**
 * Entidade Composicao - Vincula um Item a uma Etapa com sua quantidade.
 * Regra de Negócio RN01: O subtotal não é salvo em coluna agregada fixa no banco;
 * é calculado dinamicamente em tempo real (Quantidade * Item.Valor_Unitario).
 */
public class Composicao {
    private int idComposicao;
    private int idEtapa;
    private Item item;
    private double quantidade;

    public Composicao() {
    }

    public Composicao(int idComposicao, int idEtapa, Item item, double quantidade) {
        this.idComposicao = idComposicao;
        this.idEtapa = idEtapa;
        this.item = item;
        this.quantidade = quantidade;
    }

    public Composicao(int idEtapa, Item item, double quantidade) {
        this(0, idEtapa, item, quantidade);
    }

    public int getIdComposicao() {
        return idComposicao;
    }

    public void setIdComposicao(int idComposicao) {
        this.idComposicao = idComposicao;
    }

    public int getIdEtapa() {
        return idEtapa;
    }

    public void setIdEtapa(int idEtapa) {
        this.idEtapa = idEtapa;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public double getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(double quantidade) {
        this.quantidade = quantidade;
    }

    /**
     * RN01 - Cálculo Dinâmico em tempo real: Quantidade * Valor Unitário do Item.
     */
    public double getSubtotal() {
        if (item == null) {
            return 0.0;
        }
        return quantidade * item.getValorUnitario();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Composicao that = (Composicao) o;
        return idComposicao == that.idComposicao &&
                idEtapa == that.idEtapa &&
                Double.compare(that.quantidade, quantidade) == 0 &&
                Objects.equals(item, that.item);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idComposicao, idEtapa, item, quantidade);
    }

    @Override
    public String toString() {
        return (item != null ? item.getDescricao() : "Item Nulo") + 
               " - Qtd: " + quantidade + " | Subtotal: R$ " + String.format("%.2f", getSubtotal());
    }
}
