package com.procampo.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entidade Modelo - Representa o projeto ou orçamento agropecuário completo.
 * Atributos principais: ID_Modelo, Nome, Ciclo_Produtivo, Espacamento, Produtividade, Categoria.
 */
public class Modelo {
    private int idModelo;
    private String nome;
    private String cicloProdutivo;
    private String espacamento;
    private String produtividade;
    private String categoria;
    private List<Etapa> etapas = new ArrayList<>();

    public Modelo() {
    }

    public Modelo(int idModelo, String nome, String cicloProdutivo, String espacamento, String produtividade, String categoria) {
        this.idModelo = idModelo;
        this.nome = nome;
        this.cicloProdutivo = cicloProdutivo;
        this.espacamento = espacamento;
        this.produtividade = produtividade;
        this.categoria = categoria;
    }

    public Modelo(String nome, String cicloProdutivo, String espacamento, String produtividade, String categoria) {
        this(0, nome, cicloProdutivo, espacamento, produtividade, categoria);
    }

    public int getIdModelo() {
        return idModelo;
    }

    public void setIdModelo(int idModelo) {
        this.idModelo = idModelo;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCicloProdutivo() {
        return cicloProdutivo;
    }

    public void setCicloProdutivo(String cicloProdutivo) {
        this.cicloProdutivo = cicloProdutivo;
    }

    public String getEspacamento() {
        return espacamento;
    }

    public void setEspacamento(String espacamento) {
        this.espacamento = espacamento;
    }

    public String getProdutividade() {
        return produtividade;
    }

    public void setProdutividade(String produtividade) {
        this.produtividade = produtividade;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public List<Etapa> getEtapas() {
        return etapas;
    }

    public void setEtapas(List<Etapa> etapas) {
        this.etapas = etapas != null ? etapas : new ArrayList<>();
    }

    public void addEtapa(Etapa etapa) {
        if (etapa != null) {
            this.etapas.add(etapa);
        }
    }

    /**
     * RN01 - Somatório automático e dinâmico do total geral do modelo em tempo real.
     */
    public double getTotalGeral() {
        return etapas.stream()
                .mapToDouble(Etapa::getTotalEtapa)
                .sum();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Modelo modelo = (Modelo) o;
        return idModelo == modelo.idModelo &&
                Objects.equals(nome, modelo.nome);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idModelo, nome);
    }

    @Override
    public String toString() {
        return nome + " [" + (categoria != null ? categoria : "Geral") + "] - Total: R$ " + String.format("%.2f", getTotalGeral());
    }
}
