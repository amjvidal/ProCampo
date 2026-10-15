package com.procampo.controller;

import com.procampo.dao.ItemDAO;
import com.procampo.dao.ModeloDAO;
import com.procampo.model.Composicao;
import com.procampo.model.Etapa;
import com.procampo.model.Item;
import com.procampo.model.Modelo;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import com.procampo.util.PdfExportUtil;

/**
 * Controller para detalhamento por Etapas, exibição de valores e totalizadores automáticos (RF03, RF04, RF05, RF09).
 * Garante os cálculos dinâmicos e reflexo de atualizações em cascata (RN01, RN02).
 */
public class ModeloDetailController {

    @FXML private Label lblNomeModelo;
    @FXML private Label lblMetaResumo;
    @FXML private Label lblTotalGeral;
    @FXML private VBox boxMetadados;

    @FXML private TextField txtEditNome;
    @FXML private TextField txtEditCategoria;
    @FXML private TextField txtEditCiclo;
    @FXML private TextField txtEditEspacamento;
    @FXML private TextField txtEditProdutividade;

    @FXML private VBox boxEtapasContainer;

    private Modelo modeloAtual;
    private MainController mainController;
    private final ModeloDAO modeloDAO = new ModeloDAO();
    private final ItemDAO itemDAO = new ItemDAO();

    public void setModelo(Modelo modelo, MainController mainController) {
        this.mainController = mainController;
        carregarModeloCompleto(modelo.getIdModelo());
    }

    public void carregarModeloCompleto(int idModelo) {
        try {
            this.modeloAtual = modeloDAO.buscarCompletoPorId(idModelo);
            if (this.modeloAtual != null) {
                atualizarCabecalho();
                renderizarEtapas();
            }
        } catch (SQLException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Erro ao Carregar Modelo", e.getMessage());
        }
    }

    private void atualizarCabecalho() {
        lblNomeModelo.setText(modeloAtual.getNome());
        lblMetaResumo.setText(String.format("Categoria: %s | Ciclo: %s | Espaçamento: %s | Produtividade: %s",
                modeloAtual.getCategoria(), modeloAtual.getCicloProdutivo(),
                modeloAtual.getEspacamento(), modeloAtual.getProdutividade()));

        txtEditNome.setText(modeloAtual.getNome());
        txtEditCategoria.setText(modeloAtual.getCategoria());
        txtEditCiclo.setText(modeloAtual.getCicloProdutivo());
        txtEditEspacamento.setText(modeloAtual.getEspacamento());
        txtEditProdutividade.setText(modeloAtual.getProdutividade());

        // RN01 - Somatório em tempo real do total geral
        lblTotalGeral.setText("R$ " + String.format("%.2f", modeloAtual.getTotalGeral()));
    }

    private void renderizarEtapas() {
        boxEtapasContainer.getChildren().clear();

        if (modeloAtual.getEtapas().isEmpty()) {
            Label lblVazio = new Label("Nenhuma etapa cadastrada neste projeto. Clique em '+ Nova Etapa de Produção' abaixo.");
            lblVazio.setStyle("-fx-text-fill: #718096; -fx-font-style: italic; -fx-padding: 20px;");
            boxEtapasContainer.getChildren().add(lblVazio);
            return;
        }

        for (Etapa etapa : modeloAtual.getEtapas()) {
            VBox boxEtapa = criarPainelEtapa(etapa);
            boxEtapasContainer.getChildren().add(boxEtapa);
        }
    }

    private VBox criarPainelEtapa(Etapa etapa) {
        VBox box = new VBox(10);
        box.setStyle("-fx-background-color: white; -fx-padding: 16px; -fx-background-radius: 10px; -fx-border-color: #e2e8f0; -fx-border-radius: 10px;");

        // Header da Etapa
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Label lblNome = new Label(etapa.getNomeEtapa());
        lblNome.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1a202c;");

        Label lblSubtotal = new Label("Subtotal da Etapa: R$ " + String.format("%.2f", etapa.getTotalEtapa()));
        lblSubtotal.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #2e7d32; -fx-background-color: #e6fffa; -fx-padding: 4px 10px; -fx-background-radius: 6px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnAddComp = new Button("➕ Adicionar Item");
        btnAddComp.getStyleClass().add("btn-secondary");
        btnAddComp.setOnAction(e -> handleAdicionarComposicao(etapa));

        Button btnEditarEtapa = new Button("Editar Etapa");
        btnEditarEtapa.getStyleClass().add("btn-secondary");
        btnEditarEtapa.setOnAction(e -> handleEditarEtapa(etapa));

        Button btnRemoverEtapa = new Button("Excluir Etapa");
        btnRemoverEtapa.getStyleClass().add("btn-danger");
        btnRemoverEtapa.setOnAction(e -> handleRemoverEtapa(etapa));

        header.getChildren().addAll(lblNome, lblSubtotal, spacer, btnAddComp, btnEditarEtapa, btnRemoverEtapa);

        // Tabela de Itens da Etapa (Composição - RF04)
        TableView<Composicao> tblComp = new TableView<>();
        tblComp.getStyleClass().add("table-view");
        tblComp.setPrefHeight(250);
        tblComp.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<Composicao, String> colDesc = new TableColumn<>("Insumo / Mão de Obra");
        colDesc.setPrefWidth(320);
        colDesc.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getItem() != null ? data.getValue().getItem().getDescricao() : ""));

        TableColumn<Composicao, String> colUnit = new TableColumn<>("Unid.");
        colUnit.setPrefWidth(80);
        colUnit.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getItem() != null ? data.getValue().getItem().getUnidadeMedida() : ""));

        TableColumn<Composicao, String> colQtd = new TableColumn<>("Quant.");
        colQtd.setPrefWidth(90);
        colQtd.setStyle("-fx-alignment: CENTER;");
        colQtd.setCellValueFactory(data -> new SimpleStringProperty(String.format("%.2f", data.getValue().getQuantidade())));

        TableColumn<Composicao, String> colUnitVal = new TableColumn<>("Valor Unit. (R$)");
        colUnitVal.setPrefWidth(130);
        colUnitVal.setStyle("-fx-alignment: CENTER-RIGHT;");
        colUnitVal.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getItem() != null ? String.format("%.2f", data.getValue().getItem().getValorUnitario()) : "0.00"));

        TableColumn<Composicao, String> colSub = new TableColumn<>("Subtotal (R$)");
        colSub.setPrefWidth(140);
        colSub.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold;");
        colSub.setCellValueFactory(data -> new SimpleStringProperty(String.format("%.2f", data.getValue().getSubtotal())));

        TableColumn<Composicao, Void> colAcoes = new TableColumn<>("Ações");
        colAcoes.setPrefWidth(140);
        colAcoes.setStyle("-fx-alignment: CENTER;");
        colAcoes.setCellFactory(param -> new TableCell<>() {
            private final Button btnQtd = new Button("Editar");
            private final Button btnDel = new Button("Excluir");
            private final HBox pane = new HBox(6, btnQtd, btnDel);

            {
                btnQtd.getStyleClass().add("btn-secondary");
                btnDel.getStyleClass().add("btn-danger");
                pane.setAlignment(Pos.CENTER);

                btnQtd.setOnAction(e -> {
                    Composicao comp = getTableView().getItems().get(getIndex());
                    handleAlterarQuantidade(comp);
                });

                btnDel.setOnAction(e -> {
                    Composicao comp = getTableView().getItems().get(getIndex());
                    handleExcluirComposicao(comp);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : pane);
            }
        });

        tblComp.getColumns().addAll(colDesc, colUnit, colQtd, colUnitVal, colSub, colAcoes);
        tblComp.setItems(FXCollections.observableArrayList(etapa.getComposicoes()));

        box.getChildren().addAll(header, tblComp);
        return box;
    }

    @FXML
    private void toggleMetadadosBox() {
        boolean vis = !boxMetadados.isVisible();
        boxMetadados.setVisible(vis);
        boxMetadados.setManaged(vis);
    }

    @FXML
    private void handleSalvarMetadados() {
        modeloAtual.setNome(txtEditNome.getText().trim());
        modeloAtual.setCategoria(txtEditCategoria.getText().trim());
        modeloAtual.setCicloProdutivo(txtEditCiclo.getText().trim());
        modeloAtual.setEspacamento(txtEditEspacamento.getText().trim());
        modeloAtual.setProdutividade(txtEditProdutividade.getText().trim());

        try {
            if (modeloDAO.salvarModelo(modeloAtual)) {
                mostrarAlerta(Alert.AlertType.INFORMATION, "Sucesso", "Dados do orçamento salvos com sucesso.");
                toggleMetadadosBox();
                atualizarCabecalho();
            }
        } catch (SQLException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Verifique se o nome do projeto já existe: " + e.getMessage());
        }
    }

    @FXML
    private void handleNovaEtapa() {
        TextInputDialog dialog = new TextInputDialog("Nova Etapa");
        dialog.setTitle("Adicionar Etapa de Produção");
        dialog.setHeaderText("Digite o nome da nova etapa (ex: Insumos, Tratos Culturais, Colheita):");
        dialog.setContentText("Nome:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(nome -> {
            if (!nome.trim().isEmpty()) {
                Etapa nova = new Etapa(modeloAtual.getIdModelo(), nome.trim(), modeloAtual.getEtapas().size() + 1);
                try {
                    if (modeloDAO.salvarEtapa(nova)) {
                        carregarModeloCompleto(modeloAtual.getIdModelo());
                    }
                } catch (SQLException e) {
                    mostrarAlerta(Alert.AlertType.ERROR, "Erro", e.getMessage());
                }
            }
        });
    }

    private void handleEditarEtapa(Etapa etapa) {
        TextInputDialog dialog = new TextInputDialog(etapa.getNomeEtapa());
        dialog.setTitle("Renomear Etapa");
        dialog.setHeaderText("Altere o nome da etapa de produção:");
        dialog.setContentText("Nome:");

        dialog.showAndWait().ifPresent(novoNome -> {
            if (!novoNome.trim().isEmpty()) {
                etapa.setNomeEtapa(novoNome.trim());
                try {
                    if (modeloDAO.salvarEtapa(etapa)) {
                        carregarModeloCompleto(modeloAtual.getIdModelo());
                    }
                } catch (SQLException e) {
                    mostrarAlerta(Alert.AlertType.ERROR, "Erro", e.getMessage());
                }
            }
        });
    }

    private void handleRemoverEtapa(Etapa etapa) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, 
                "Excluir a etapa '" + etapa.getNomeEtapa() + "' e todos os seus itens da composição?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirmação de Exclusão");
        confirm.setHeaderText(null);

        if (confirm.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
            try {
                if (modeloDAO.excluirEtapa(etapa.getIdEtapa())) {
                    carregarModeloCompleto(modeloAtual.getIdModelo());
                }
            } catch (SQLException e) {
                mostrarAlerta(Alert.AlertType.ERROR, "Erro", e.getMessage());
            }
        }
    }

    private void handleAdicionarComposicao(Etapa etapa) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Adicionar Item à Etapa: " + etapa.getNomeEtapa());

        VBox layout = new VBox(14);
        layout.setPadding(new Insets(20));
        layout.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        layout.getStyleClass().add("card");
        layout.setPrefWidth(420);

        Label lbl = new Label("Selecione um Insumo/Mão de Obra do Catálogo:");
        lbl.setStyle("-fx-font-weight: bold;");

        org.controlsfx.control.SearchableComboBox<Item> cbItens = new org.controlsfx.control.SearchableComboBox<>();
        cbItens.setMaxWidth(Double.MAX_VALUE);
        cbItens.getStyleClass().add("combo-box");
        try {
            List<Item> lista = itemDAO.listarTodos();
            cbItens.setItems(FXCollections.observableArrayList(lista));
            if (!lista.isEmpty()) cbItens.getSelectionModel().selectFirst();
        } catch (SQLException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Erro", "Não foi possível carregar o catálogo de itens.");
            return;
        }

        Label lblQtd = new Label("Quantidade / Horas / Metragem:");
        lblQtd.setStyle("-fx-font-weight: bold;");
        TextField txtQtd = new TextField("1");
        txtQtd.getStyleClass().add("text-field");

        Button btnSalvar = new Button("💾 Vincular à Etapa");
        btnSalvar.getStyleClass().add("btn-primary");
        btnSalvar.setMaxWidth(Double.MAX_VALUE);
        btnSalvar.setOnAction(e -> {
            Item sel = cbItens.getValue();
            if (sel == null) return;
            try {
                double qtd = Double.parseDouble(txtQtd.getText().replace(",", ".").trim());
                if (qtd <= 0) throw new NumberFormatException();

                Composicao comp = new Composicao(etapa.getIdEtapa(), sel, qtd);
                if (modeloDAO.salvarComposicao(comp)) {
                    dialog.close();
                    carregarModeloCompleto(modeloAtual.getIdModelo());
                }
            } catch (NumberFormatException ex) {
                mostrarAlerta(Alert.AlertType.WARNING, "Aviso", "Informe uma quantidade válida e positiva.");
            } catch (SQLException ex) {
                mostrarAlerta(Alert.AlertType.ERROR, "Erro no Banco", ex.getMessage());
            }
        });

        layout.getChildren().addAll(lbl, cbItens, lblQtd, txtQtd, btnSalvar);
        dialog.setScene(new Scene(layout));
        dialog.showAndWait();
    }

    private void handleAlterarQuantidade(Composicao comp) {
        TextInputDialog dialog = new TextInputDialog(String.valueOf(comp.getQuantidade()));
        dialog.setTitle("Alterar Quantidade");
        dialog.setHeaderText("Insumo/Item: " + comp.getItem().getDescricao());
        dialog.setContentText("Nova Quantidade (" + comp.getItem().getUnidadeMedida() + "):");

        dialog.showAndWait().ifPresent(val -> {
            try {
                double novaQtd = Double.parseDouble(val.replace(",", ".").trim());
                if (novaQtd <= 0) throw new NumberFormatException();

                comp.setQuantidade(novaQtd);
                if (modeloDAO.salvarComposicao(comp)) {
                    carregarModeloCompleto(modeloAtual.getIdModelo());
                }
            } catch (NumberFormatException e) {
                mostrarAlerta(Alert.AlertType.WARNING, "Número Inválido", "Informe uma quantidade válida positiva.");
            } catch (SQLException e) {
                mostrarAlerta(Alert.AlertType.ERROR, "Erro", e.getMessage());
            }
        });
    }

    private void handleExcluirComposicao(Composicao comp) {
        try {
            if (modeloDAO.excluirComposicao(comp.getIdComposicao())) {
                carregarModeloCompleto(modeloAtual.getIdModelo());
            }
        } catch (SQLException e) {
            mostrarAlerta(Alert.AlertType.ERROR, "Erro ao Excluir", e.getMessage());
        }
    }

    @FXML
    private void handleVoltar() {
        mainController.showModelosView();
    }

    @FXML
    private void handleExportarPDF() {
        if (modeloAtual == null) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exportar Orçamento para PDF");
        fileChooser.setInitialFileName(modeloAtual.getNome().replaceAll("[^a-zA-Z0-9.-]", "_") + ".pdf");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF (*.pdf)", "*.pdf"));

        File dest = fileChooser.showSaveDialog(boxEtapasContainer.getScene().getWindow());
        if (dest != null) {
            try {
                PdfExportUtil.exportModeloToPdf(modeloAtual, dest);
                mostrarAlerta(Alert.AlertType.INFORMATION, "Exportação Concluída", "Orçamento exportado com sucesso para:\n" + dest.getAbsolutePath());
            } catch (Exception e) {
                mostrarAlerta(Alert.AlertType.ERROR, "Erro na Exportação", "Falha ao gerar PDF:\n" + e.getMessage());
            }
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
