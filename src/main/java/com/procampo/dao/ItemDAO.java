package com.procampo.dao;

import com.procampo.db.DatabaseConnection;
import com.procampo.model.Item;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para gerenciamento e consulta do Catálogo de Preços (RF06, RF07, RN03).
 */
public class ItemDAO {

    /**
     * RF01 / RF07 - Listar todos os itens cadastrados no catálogo por ordem alfabética.
     */
    public List<Item> listarTodos() throws SQLException {
        List<Item> itens = new ArrayList<>();
        String sql = "SELECT * FROM Item ORDER BY Descricao ASC";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                itens.add(mapearItem(rs));
            }
        }
        return itens;
    }

    /**
     * RF06 - Busca Global de Preços: consulta rápida no catálogo por descrição ou unidade.
     */
    public List<Item> buscarPorTermo(String termo) throws SQLException {
        List<Item> itens = new ArrayList<>();
        String sql = "SELECT * FROM Item WHERE Descricao LIKE ? OR Unidade_Medida LIKE ? ORDER BY Descricao ASC";

        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            String pad = "%" + (termo != null ? termo.trim() : "") + "%";
            ps.setString(1, pad);
            ps.setString(2, pad);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    itens.add(mapearItem(rs));
                }
            }
        }
        return itens;
    }

    public Item buscarPorId(int idItem) throws SQLException {
        String sql = "SELECT * FROM Item WHERE ID_Item = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, idItem);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapearItem(rs);
                }
            }
        }
        return null;
    }

    /**
     * RF07 - Adição e Edição de Itens no Catálogo de Preços.
     */
    public boolean salvar(Item item) throws SQLException {
        if (item.getIdItem() <= 0) {
            return inserir(item);
        } else {
            return atualizar(item);
        }
    }

    private boolean inserir(Item item) throws SQLException {
        String sql = "INSERT INTO Item (Descricao, Unidade_Medida, Valor_Unitario, Categoria) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, item.getDescricao().trim());
            ps.setString(2, item.getUnidadeMedida().trim());
            ps.setDouble(3, item.getValorUnitario());
            ps.setString(4, item.getCategoria() != null ? item.getCategoria().trim() : "");

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        item.setIdItem(rs.getInt(1));
                    }
                }
                return true;
            }
        }
        return false;
    }

    private boolean atualizar(Item item) throws SQLException {
        String sql = "UPDATE Item SET Descricao = ?, Unidade_Medida = ?, Valor_Unitario = ?, Categoria = ? WHERE ID_Item = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, item.getDescricao().trim());
            ps.setString(2, item.getUnidadeMedida().trim());
            ps.setDouble(3, item.getValorUnitario());
            ps.setString(4, item.getCategoria() != null ? item.getCategoria().trim() : "");
            ps.setInt(5, item.getIdItem());

            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Verifica proativamente se o item está em uso em alguma Composição ativa (RN03).
     */
    public boolean isItemEmUso(int idItem) throws SQLException {
        String sql = "SELECT COUNT(*) AS total FROM Composicao WHERE ID_Item = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, idItem);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total") > 0;
                }
            }
        }
        return false;
    }

    /**
     * Remoção de item do Catálogo com verificação de uso em composições.
     */
    public boolean excluir(int idItem) throws SQLException {
        if (isItemEmUso(idItem)) {
            throw new SQLException("Bloqueio de Exclusão: Este item não pode ser excluído pois está vinculado a uma ou mais composições de modelos ativos no sistema.");
        }

        String sql = "DELETE FROM Item WHERE ID_Item = ?";
        try (Connection conn = DatabaseConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, idItem);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            if (e.getMessage() != null && (e.getMessage().contains("FOREIGN KEY") || e.getMessage().contains("foreign key"))) {
                throw new SQLException("Bloqueio de Exclusão: Este item não pode ser excluído pois está vinculado a uma ou mais composições no catálogo.", e);
            }
            throw e;
        }
    }

    private Item mapearItem(ResultSet rs) throws SQLException {
        return new Item(
                rs.getInt("ID_Item"),
                rs.getString("Descricao"),
                rs.getString("Unidade_Medida"),
                rs.getDouble("Valor_Unitario"),
                rs.getString("Categoria")
        );
    }
}
