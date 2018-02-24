import { Component, OnInit } from '@angular/core';
import { Diagram } from '../model/diagram';
import { DiagramService, SelectionUpdateEvent } from '../service/diagram.service';

@Component({
  selector: 'app-diagram',
  templateUrl: './diagram.component.html',
  styleUrls: ['./diagram.component.css']
})
export class DiagramComponent implements OnInit {
  diagrams : Array<Diagram>;
  ngClass: any;
  constructor(private _diagramService: DiagramService) {
    this.diagrams = new Array<Diagram>();
    this._diagramService.SelectiponUpdateEvent.subscribe((selectionUpdateEvent: SelectionUpdateEvent) =>{
      if(selectionUpdateEvent.EventType == "Added"){
        this.diagrams.push(selectionUpdateEvent.Diagram);
        this.updateClass();
      }else if(selectionUpdateEvent.EventType == "Removed"){
        this.diagrams.splice(this.diagrams.indexOf(selectionUpdateEvent.Diagram),1);
        this.updateClass();
      }else if(selectionUpdateEvent.EventType == "Clear"){
        this.diagrams = new Array<Diagram>();
        this.updateClass();
      }
    });
  }

  updateClass(){
    this.ngClass= {
      "col" : true,
      "col-lg-12" : this.diagrams.length < 2,
      "col-lg-6" : this.diagrams.length >=2 && this.diagrams.length < 6,
      "col-lg-4" : this.diagrams.length >=6
    }
  }
  ngOnInit() {
  }

}
