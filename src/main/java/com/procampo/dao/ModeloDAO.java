package com.procampo.dao;

import com.procampo.db.DatabaseConnection;
import com.procampo.model.Composicao;
import com.procampo.model.Etapa;
import com.procampo.model.Item;
import com.procampo.model.Modelo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO responsável pelo CRUD de Modelos (RF01, RF02, RF08) e de toda sua hierarquia
 * de Etapas e Composições (RF03, RF04, RF05, RF09).
 * Garante os cálculos dinâmicos em tempo real sem persistir totais fixos (RN01)
 * e o reflexo imediato das alterações do catálogo de preços em todos os orçamentos (RN02).
 */
public class ModeloDAO {

    private final ItemDAO itemDAO = new ItemDAO();

    /**
     * RF01 - Catálogo de Modelos: Listagem completa resumida dos modelos cadastrados.
     */
    public List<Modelo> listarTodos() throws SQLException {
        return buscarPorNomeECategoria("", "");
    }

    /**
     * RF02 - Busca e Filtros: Consulta modelos por nome e por categoria.
     * Além dos dados básicos, carrega as etapas e composições para que getTotalGeral() reflita o somatório dinâmico (RN01).
     */
    public List<Modelo> buscarPorNomeECategoria(String nome, String categoria) throws SQLException {
        List<Modelo> modelos = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM Modelo WHERE 1=1 ");

        if (nome != null && !nome.trim().isEmpty()) {
            sql.append("AND Nome LIKE ? ");
        }
        if (categoria != null && !categoria.trim().isEmpty() && !categoria.equalsIgnoreCase("Todas")) {
            sql.append("AND Categoria = ? ");
        }
        sql.append("ORDER BY Nome ASC");

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            int index = 1;
            if (nome != null && !nome.trim().isEmpty()) {
                ps.setString(index++, "%" + nome.trim() + "%");
            }
            if (categoria != null && !categoria.trim().isEmpty() && !categoria.equalsIgnoreCase("Todas")) {
                ps.setString(index++, categoria.trim());
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Modelo m = mapearModeloBasico(rs);
                    // Carrega etapas para cálculo em tempo real de totais na listagem
                    m.setEtapas(buscarEtapasPorModelo(conn, m.getIdModelo()));
                    modelos.add(m);
                }
            }
        }
        return modelos;
    }

    /**
     * RF03 / RF04 / RF05 - Detalhamento Completo por Etapas e Itens, calculando subtotal e totais em tempo real (RN01 / RN02).
     */
    public Modelo buscarCompletoPorId(int idModelo) throws SQLException {
        String sql = "SELECT * FROM Modelo WHERE ID_Modelo = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, idModelo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Modelo modelo = mapearModeloBasico(rs);
                    modelo.setEtapas(buscarEtapasPorModelo(conn, modelo.getIdModelo()));
                    return modelo;
                }
            }
        }
        return null;
    }

    private List<Etapa> buscarEtapasPorModelo(Connection conn, int idModelo) throws SQLException {
        List<Etapa> etapas = new ArrayList<>();
        String sql = "SELECT * FROM Etapa WHERE ID_Modelo = ? ORDER BY Ordem_Exibicao ASC, ID_Etapa ASC";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idModelo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Etapa e = new Etapa(
                            rs.getInt("ID_Etapa"),
                            rs.getInt("ID_Modelo"),
                            rs.getString("Nome_Etapa"),
                            rs.getInt("Ordem_Exibicao")
                    );
                    e.setComposicoes(buscarComposicoesPorEtapa(conn, e.getIdEtapa()));
                    etapas.add(e);
                }
            }
        }
        return etapas;
    }

    private List<Composicao> buscarComposicoesPorEtapa(Connection conn, int idEtapa) throws SQLException {
        List<Composicao> composicoes = new ArrayList<>();
        String sql = """
                SELECT c.ID_Composicao, c.ID_Etapa, c.Quantidade,
                       i.ID_Item, i.Descricao, i.Unidade_Medida, i.Valor_Unitario
                FROM Composicao c
                JOIN Item i ON c.ID_Item = i.ID_Item
                WHERE c.ID_Etapa = ?
                ORDER BY i.Descricao ASC
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idEtapa);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Item item = new Item(
                            rs.getInt("ID_Item"),
                            rs.getString("Descricao"),
                            rs.getString("Unidade_Medida"),
                            rs.getDouble("Valor_Unitario")
                    );
                    Composicao c = new Composicao(
                            rs.getInt("ID_Composicao"),
                            rs.getInt("ID_Etapa"),
                            item,
                            rs.getDouble("Quantidade")
                    );
                    composicoes.add(c);
                }
            }
        }
        return composicoes;
    }

    /**
     * RF08 - Criação e Edição de Modelos.
     */
    public boolean salvarModelo(Modelo modelo) throws SQLException {
        if (modelo.getIdModelo() <= 0) {
            String sql = "INSERT INTO Modelo (Nome, Ciclo_Produtivo, Espacamento, Produtividade, Categoria) VALUES (?, ?, ?, ?, ?)";
            try (Connection conn = DatabaseConnection.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, modelo.getNome().trim());
                ps.setString(2, modelo.getCicloProdutivo() != null ? modelo.getCicloProdutivo().trim() : "-");
                ps.setString(3, modelo.getEspacamento() != null ? modelo.getEspacamento().trim() : "-");
                ps.setString(4, modelo.getProdutividade() != null ? modelo.getProdutividade().trim() : "-");
                ps.setString(5, modelo.getCategoria() != null ? modelo.getCategoria().trim() : "Geral");

                int affected = ps.executeUpdate();
                if (affected > 0) {
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            modelo.setIdModelo(rs.getInt(1));
                        }
                    }
                    return true;
                }
            }
        } else {
            String sql = "UPDATE Modelo SET Nome = ?, Ciclo_Produtivo = ?, Espacamento = ?, Produtividade = ?, Categoria = ? WHERE ID_Modelo = ?";
            try (Connection conn = DatabaseConnection.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, modelo.getNome().trim());
                ps.setString(2, modelo.getCicloProdutivo() != null ? modelo.getCicloProdutivo().trim() : "-");
                ps.setString(3, modelo.getEspacamento() != null ? modelo.getEspacamento().trim() : "-");
                ps.setString(4, modelo.getProdutividade() != null ? modelo.getProdutividade().trim() : "-");
                ps.setString(5, modelo.getCategoria() != null ? modelo.getCategoria().trim() : "Geral");
                ps.setInt(6, modelo.getIdModelo());

                return ps.executeUpdate() > 0;
            }
        }
        return false;
    }

    /**
     * RF08 - Exclusão de Modelo e suas Etapas/Composições (via CASCADE).
     */
    public boolean excluirModelo(int idModelo) throws SQLException {
        String sql = "DELETE FROM Modelo WHERE ID_Modelo = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, idModelo);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * RF09 - Gestão de Etapas do Projeto.
     */
    public boolean salvarEtapa(Etapa etapa) throws SQLException {
        if (etapa.getIdEtapa() <= 0) {
            String sql = "INSERT INTO Etapa (ID_Modelo, Nome_Etapa, Ordem_Exibicao) VALUES (?, ?, ?)";
            try (Connection conn = DatabaseConnection.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                ps.setInt(1, etapa.getIdModelo());
                ps.setString(2, etapa.getNomeEtapa().trim());
                ps.setInt(3, etapa.getOrdemExibicao() > 0 ? etapa.getOrdemExibicao() : 100);

                int affected = ps.executeUpdate();
                if (affected > 0) {
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            etapa.setIdEtapa(rs.getInt(1));
                        }
                    }
                    return true;
                }
            }
        } else {
            String sql = "UPDATE Etapa SET Nome_Etapa = ?, Ordem_Exibicao = ? WHERE ID_Etapa = ?";
            try (Connection conn = DatabaseConnection.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setString(1, etapa.getNomeEtapa().trim());
                ps.setInt(2, etapa.getOrdemExibicao());
                ps.setInt(3, etapa.getIdEtapa());

                return ps.executeUpdate() > 0;
            }
        }
        return false;
    }

    public boolean excluirEtapa(int idEtapa) throws SQLException {
        String sql = "DELETE FROM Etapa WHERE ID_Etapa = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, idEtapa);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * RF09 - Gestão de Composições (vincular itens/quantidades à etapa).
     */
    public boolean salvarComposicao(Composicao composicao) throws SQLException {
        if (composicao.getItem() == null || composicao.getItem().getIdItem() <= 0) {
            throw new SQLException("O item selecionado é inválido.");
        }
        if (composicao.getQuantidade() <= 0) {
            throw new SQLException("A quantidade deve ser maior que zero.");
        }

        if (composicao.getIdComposicao() <= 0) {
            String sql = "INSERT INTO Composicao (ID_Etapa, ID_Item, Quantidade) VALUES (?, ?, ?)";
            try (Connection conn = DatabaseConnection.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                ps.setInt(1, composicao.getIdEtapa());
                ps.setInt(2, composicao.getItem().getIdItem());
                ps.setDouble(3, composicao.getQuantidade());

                int affected = ps.executeUpdate();
                if (affected > 0) {
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            composicao.setIdComposicao(rs.getInt(1));
                        }
                    }
                    return true;
                }
            }
        } else {
            String sql = "UPDATE Composicao SET Quantidade = ?, ID_Item = ? WHERE ID_Composicao = ?";
            try (Connection conn = DatabaseConnection.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setDouble(1, composicao.getQuantidade());
                ps.setInt(2, composicao.getItem().getIdItem());
                ps.setInt(3, composicao.getIdComposicao());

                return ps.executeUpdate() > 0;
            }
        }
        return false;
    }

    public boolean excluirComposicao(int idComposicao) throws SQLException {
        String sql = "DELETE FROM Composicao WHERE ID_Composicao = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, idComposicao);
            return ps.executeUpdate() > 0;
        }
    }

    private Modelo mapearModeloBasico(ResultSet rs) throws SQLException {
        return new Modelo(
                rs.getInt("ID_Modelo"),
                rs.getString("Nome"),
                rs.getString("Ciclo_Produtivo"),
                rs.getString("Espacamento"),
                rs.getString("Produtividade"),
                rs.getString("Categoria")
        );
    }
}
