package com.procampo.controller;

import com.procampo.dao.ItemDAO;
import com.procampo.model.Item;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.sql.SQLException;
import java.util.List;

/**
 * Controller para gestão do Catálogo Global de Preços (RF06, RF07) e verificação de exclusão (RN03).
 */
public class ItemFormController {

    @FXML private TextField txtBuscaItem;
    @FXML private TableView<Item> tblItens;
    @FXML private TableColumn<Item, String> colItemDesc;
    @FXML private TableColumn<Item, String> colItemCategoria;
    @FXML private TableColumn<Item, String> colItemUnit;
    @FXML private TableColumn<Item, String> colItemValor;

    @FXML private Label lblFormTitle;
    @FXML private TextField txtDescricao;
    @FXML private TextField txtUnidade;
    @FXML private TextField txtCategoria;
    @FXML private TextField txtValor;

    private final ItemDAO itemDAO = new ItemDAO();
    private final ObservableList<Item> listaItens = FXCollections.observableArrayList();
    private Item itemSelecionado;

    @FXML
    public void initialize() {
        colItemDesc.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDescricao()));
        colItemCategoria.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCategoria()));
        colItemUnit.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getUnidadeMedida()));
        colItemValor.setCellValueFactory(data -> new SimpleStringProperty(String.format("%.2f", data.getValue().getValorUnitario())));

        tblItens.setItems(listaItens);
        tblItens.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        // Listener de seleção na tabela
        tblItens.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                preencherFormulario(newSel);
            }
        });

        // Busca rápida ao digitar
        txtBuscaItem.textProperty().addListener((obs, oldV, newV) -> handleBuscarItens());

        carregarDados();
    }

    public void carregarDados() {
        try {
            listaItens.setAll(itemDAO.listarTodos());
        } catch (SQLException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Erro no Banco de Dados", "Falha ao carregar catálogo de itens: " + e.getMessage());
        }
    }

    @FXML
    private void handleBuscarItens() {
        try {
            String termo = txtBuscaItem.getText();
            if (termo == null || termo.trim().isEmpty()) {
                carregarDados();
            } else {
                listaItens.setAll(itemDAO.buscarPorTermo(termo));
            }
        } catch (SQLException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Erro na Busca", e.getMessage());
        }
    }

    @FXML
    private void handleListarTodos() {
        txtBuscaItem.clear();
        carregarDados();
    }

    private void preencherFormulario(Item item) {
        this.itemSelecionado = item;
        lblFormTitle.setText("✏️ Editando Item #" + item.getIdItem());
        txtDescricao.setText(item.getDescricao());
        txtCategoria.setText(item.getCategoria());
        txtUnidade.setText(item.getUnidadeMedida());
        txtValor.setText(String.valueOf(item.getValorUnitario()));
    }

    @FXML
    private void handleLimparFormulario() {
        tblItens.getSelectionModel().clearSelection();
        this.itemSelecionado = null;
        lblFormTitle.setText("➕ Novo Item de Preço");
        txtDescricao.clear();
        txtCategoria.clear();
        txtUnidade.clear();
        txtValor.clear();
    }

    @FXML
    private void handleSalvarItem() {
        String desc = txtDescricao.getText();
        String unit = txtUnidade.getText();
        String cat = txtCategoria.getText();
        String valStr = txtValor.getText();

        if (desc == null || desc.trim().isEmpty() || unit == null || unit.trim().isEmpty() || valStr == null || valStr.trim().isEmpty()) {
            mostrarAlerta(Alert.AlertType.WARNING, "Campos Obrigatórios", "Por favor, preencha Descrição, Unidade e Valor Unitário.");
            return;
        }

        double valor;
        try {
            valor = Double.parseDouble(valStr.replace(",", ".").trim());
            if (valor < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            mostrarAlerta(Alert.AlertType.WARNING, "Valor Inválido", "Informe um valor numérico válido positivo (ex: 120.50).");
            return;
        }

        Item novoOuEditado = (itemSelecionado != null) ? itemSelecionado : new Item();
        novoOuEditado.setDescricao(desc.trim());
        novoOuEditado.setCategoria(cat != null ? cat.trim() : "");
        novoOuEditado.setUnidadeMedida(unit.trim());
        novoOuEditado.setValorUnitario(valor);

        try {
            boolean sucesso = itemDAO.salvar(novoOuEditado);
            if (sucesso) {
                mostrarAlerta(Alert.AlertType.INFORMATION, "Catálogo Atualizado", 
                        "Item salvo com sucesso! O novo valor será refletido em todos os orçamentos que utilizam este item.");
                handleLimparFormulario();
                carregarDados();
            } else {
                mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Não foi possível salvar o item.");
            }
        } catch (SQLException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Erro ao Salvar", "Verifique se a descrição já existe no catálogo:\n" + e.getMessage());
        }
    }

    @FXML
    private void handleExcluirItem() {
        Item sel = tblItens.getSelectionModel().getSelectedItem();
        if (sel == null) {
            mostrarAlerta(Alert.AlertType.WARNING, "Nenhum Item Selecionado", "Selecione um item na tabela para excluir.");
            return;
        }

        try {
            boolean sucesso = itemDAO.excluir(sel.getIdItem());
            if (sucesso) {
                mostrarAlerta(Alert.AlertType.INFORMATION, "Item Excluído", "O item foi removido com sucesso do catálogo.");
                handleLimparFormulario();
                carregarDados();
            }
        } catch (SQLException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Bloqueio de Segurança no Catálogo", e.getMessage());
        }
    }

    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String msg) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
