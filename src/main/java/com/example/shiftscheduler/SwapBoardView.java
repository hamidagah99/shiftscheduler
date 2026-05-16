package com.example.shiftscheduler;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.stream.Collectors;

@PermitAll
@Route("tauschboerse")
public class SwapBoardView extends VerticalLayout {

    public SwapBoardView(ShiftRepository shiftRepo, EmployeeRepository empRepo) {
        
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        Employee currentEmp = empRepo.findAll().stream()
                .filter(e -> e.getName().toLowerCase().replace(" ", "").contains(currentUsername))
                .findFirst().orElse(null);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);
        header.setAlignItems(Alignment.CENTER);
        header.add(new H1("🤝 Öffentliche Tauschbörse"));
        header.add(new RouterLink("⬅️ Zurück zum Plan", MainView.class));
        add(header);

        add(new H3("Verfügbare Schichten"));

        Grid<Shift> tradeGrid = new Grid<>(Shift.class, false);
        tradeGrid.addColumn(shift -> shift.getEmployee().getName()).setHeader("Abgeber");
        tradeGrid.addColumn(Shift::getLabel).setHeader("Schicht");
        tradeGrid.addColumn(shift -> shift.getStartTime().toLocalDate()).setHeader("Datum");
        tradeGrid.addColumn(Shift::getTradeNotes).setHeader("Bedingung/Wunsch").setAutoWidth(true);

        tradeGrid.addComponentColumn(shift -> {
            // If an offer is already pending, lock it
            if (shift.getOfferedShiftId() != null) {
                return new Span("⏳ Angebot ausstehend");
            }
            
            // You can't offer on your own shift
            if (currentEmp != null && shift.getEmployee().getId().equals(currentEmp.getId())) {
                return new Span("Dein Inserat");
            }

            Button offerBtn = new Button("Tausch anbieten", click -> {
                Dialog dialog = new Dialog();
                dialog.setHeaderTitle("Welche Schicht bietest du im Tausch an?");
                
                ComboBox<Shift> myShifts = new ComboBox<>("Meine Schichten");
                List<Shift> userShifts = shiftRepo.findAll().stream()
                        .filter(s -> s.getEmployee().getId().equals(currentEmp.getId()))
                        .collect(Collectors.toList());
                        
                myShifts.setItems(userShifts);
                myShifts.setItemLabelGenerator(s -> s.getLabel() + " am " + s.getStartTime().toLocalDate());

                Button confirmBtn = new Button("Angebot senden", e -> {
                    if (myShifts.getValue() != null) {
                        shift.setOfferedShiftId(myShifts.getValue().getId());
                        shift.setOfferedByUsername(currentUsername);
                        shiftRepo.save(shift);
                        
                        dialog.close();
                        Notification.show("Angebot an " + shift.getEmployee().getName() + " gesendet!", 3000, Notification.Position.MIDDLE);
                        getUI().ifPresent(ui -> ui.getPage().reload());
                    }
                });
                confirmBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
                dialog.add(new VerticalLayout(myShifts, confirmBtn));
                dialog.open();
            });
            offerBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            return offerBtn;
        }).setHeader("Aktion").setAutoWidth(true);

        List<Shift> openShifts = shiftRepo.findAll().stream()
                .filter(Shift::isTradeOpen)
                .collect(Collectors.toList());
        tradeGrid.setItems(openShifts);

        add(tradeGrid);
    }
}