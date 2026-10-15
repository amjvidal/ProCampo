package com.procampo.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entidade Etapa - Representa o agrupamento de itens/procedimentos dentro do projeto agropecuário.
 * Atributos principais: ID_Etapa, ID_Modelo (FK), Nome_Etapa, Ordem_Exibicao.
 */
public class Etapa {
    private int idEtapa;
    private int idModelo;
    private String nomeEtapa;
    private int ordemExibicao;
    private List<Composicao> composicoes = new ArrayList<>();

    public Etapa() {
    }

    public Etapa(int idEtapa, int idModelo, String nomeEtapa, int ordemExibicao) {
        this.idEtapa = idEtapa;
        this.idModelo = idModelo;
        this.nomeEtapa = nomeEtapa;
        this.ordemExibicao = ordemExibicao;
    }

    public Etapa(int idModelo, String nomeEtapa, int ordemExibicao) {
        this(0, idModelo, nomeEtapa, ordemExibicao);
    }

    public int getIdEtapa() {
        return idEtapa;
    }

    public void setIdEtapa(int idEtapa) {
        this.idEtapa = idEtapa;
    }

    public int getIdModelo() {
        return idModelo;
    }

    public void setIdModelo(int idModelo) {
        this.idModelo = idModelo;
    }

    public String getNomeEtapa() {
        return nomeEtapa;
    }

    public void setNomeEtapa(String nomeEtapa) {
        this.nomeEtapa = nomeEtapa;
    }

    public int getOrdemExibicao() {
        return ordemExibicao;
    }

    public void setOrdemExibicao(int ordemExibicao) {
        this.ordemExibicao = ordemExibicao;
    }

    public List<Composicao> getComposicoes() {
        return composicoes;
    }

    public void setComposicoes(List<Composicao> composicoes) {
        this.composicoes = composicoes != null ? composicoes : new ArrayList<>();
    }

    public void addComposicao(Composicao composicao) {
        if (composicao != null) {
            this.composicoes.add(composicao);
        }
    }

    /**
     * RN01 - Somatório automático dinâmico da etapa em tempo real.
     */
    public double getTotalEtapa() {
        return composicoes.stream()
                .mapToDouble(Composicao::getSubtotal)
                .sum();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Etapa etapa = (Etapa) o;
        return idEtapa == etapa.idEtapa &&
                idModelo == etapa.idModelo &&
                ordemExibicao == etapa.ordemExibicao &&
                Objects.equals(nomeEtapa, etapa.nomeEtapa);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idEtapa, idModelo, nomeEtapa, ordemExibicao);
    }

    @Override
    public String toString() {
        return nomeEtapa + " (R$ " + String.format("%.2f", getTotalEtapa()) + ")";
    }
}
