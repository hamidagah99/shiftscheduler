package com.example.shiftscheduler;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@PermitAll
@Route("tauschboerse")
public class SwapBoardView extends VerticalLayout {

    public SwapBoardView(ShiftRepository shiftRepo, EmployeeRepository empRepo) {
        
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        String searchName = currentUsername.replace("emp", "employee ");
        
        Employee currentEmp = empRepo.findAll().stream()
                .filter(e -> e.getName().toLowerCase().contains(currentUsername) || 
                             e.getName().toLowerCase().contains(searchName))
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
            
            boolean isMyShift = currentEmp != null && shift.getEmployee().getId().equals(currentEmp.getId());

            // 1. If it is my shift...
            if (isMyShift) {
                HorizontalLayout myActions = new HorizontalLayout();
                
                if (shift.getOfferedShiftIds() != null) {
                    Button reviewBtn = new Button("Angebot prüfen", click -> reviewOffer(shift, shiftRepo));
                    reviewBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_PRIMARY);
                    myActions.add(reviewBtn);
                }

                //  Cancel Request Button
                Button cancelBtn = new Button("Inserat löschen", click -> {
                    shift.setTradeOpen(false);
                    shift.setTradeNotes("");
                    shift.setOfferedShiftIds(null);
                    shift.setOfferedByUsername(null);
                    shiftRepo.save(shift);
                    getUI().ifPresent(ui -> ui.getPage().reload());
                });
                cancelBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
                myActions.add(cancelBtn);
                
                return myActions;
            }

            // 2. If an offer is pending on someone else's shift
            if (shift.getOfferedShiftIds() != null) {
                return new Span("⏳ Angebot ausstehend");
            }

            // 3. Otherwise, allow an offer to be made
            Button offerBtn = new Button("Tausch anbieten", click -> {
                if (currentEmp == null) return;

                Dialog dialog = new Dialog();
                dialog.setHeaderTitle("Welche Schichten bietest du an?");
                
                // MultiSelectComboBox to choose multiple shifts
                MultiSelectComboBox<Shift> myShifts = new MultiSelectComboBox<>("Meine Schichten (mehrere möglich)");
                myShifts.setWidthFull();
                List<Shift> userShifts = shiftRepo.findAll().stream()
                        .filter(s -> s.getEmployee().getId().equals(currentEmp.getId()))
                        .collect(Collectors.toList());
                        
                myShifts.setItems(userShifts);
                myShifts.setItemLabelGenerator(s -> s.getLabel() + " am " + s.getStartTime().toLocalDate());

                Button confirmBtn = new Button("Angebot senden", e -> {
                    if (myShifts.getValue() != null && !myShifts.getValue().isEmpty()) {
                        
                        String selectedIds = myShifts.getValue().stream()
                            .map(s -> String.valueOf(s.getId()))
                            .collect(Collectors.joining(","));
                            
                        shift.setOfferedShiftIds(selectedIds);
                        shift.setOfferedByUsername(currentUsername);
                        shiftRepo.save(shift);
                        
                        dialog.close();
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

    private void reviewOffer(Shift shift, ShiftRepository shiftRepo) {
        Dialog dialog = new Dialog();
        
        // Parse the comma-separated IDs
        List<Long> ids = Arrays.stream(shift.getOfferedShiftIds().split(","))
                               .map(Long::valueOf)
                               .collect(Collectors.toList());
                               
        List<Shift> offeredShifts = shiftRepo.findAllById(ids);
        
        if (!offeredShifts.isEmpty()) {
            dialog.setHeaderTitle("Tauschangebot erhalten!");
            VerticalLayout dialogLayout = new VerticalLayout();
            dialogLayout.add(new Span("Benutzer '" + shift.getOfferedByUsername() + "' bietet folgende Schichten an. Wähle EINE aus:"));
            
            // Radio buttons to pick exactly one shift from the options
            RadioButtonGroup<Shift> shiftSelector = new RadioButtonGroup<>();
            shiftSelector.setItems(offeredShifts);
            shiftSelector.setItemLabelGenerator(s -> s.getLabel() + " am " + s.getStartTime().toLocalDate());
            dialogLayout.add(shiftSelector);
            
            Button acceptBtn = new Button("Tausch Bestätigen", e -> {
                Shift selectedShift = shiftSelector.getValue();
                if (selectedShift == null) {
                    Notification.show("Bitte wähle eine Schicht aus!");
                    return;
                }
                
                Employee myEmp = shift.getEmployee();
                Employee theirEmp = selectedShift.getEmployee();
                
                shift.setEmployee(theirEmp);
                selectedShift.setEmployee(myEmp);
                
                shift.setTradeOpen(false);
                shift.setTradeNotes("");
                shift.setOfferedShiftIds(null);
                shift.setOfferedByUsername(null);
                
                selectedShift.setTradeOpen(false);
                selectedShift.setTradeNotes("");
                selectedShift.setOfferedShiftIds(null);
                selectedShift.setOfferedByUsername(null);
                
                shiftRepo.save(shift);
                shiftRepo.save(selectedShift);
                
                dialog.close();
                getUI().ifPresent(ui -> ui.getPage().reload());
            });
            acceptBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_PRIMARY);
            
            Button rejectBtn = new Button("Ablehnen", e -> {
                shift.setOfferedShiftIds(null);
                shift.setOfferedByUsername(null);
                shiftRepo.save(shift);
                dialog.close();
                getUI().ifPresent(ui -> ui.getPage().reload());
            });
            rejectBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
            
            dialogLayout.add(new HorizontalLayout(acceptBtn, rejectBtn));
            dialog.add(dialogLayout);
            dialog.open();
        }
    }
}